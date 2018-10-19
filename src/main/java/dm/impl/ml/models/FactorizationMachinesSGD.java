package dm.impl.ml.models;

import dm.interfaces.SGD;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


public class FactorizationMachinesSGD implements SGD,Serializable
{

    protected Map<Integer,Double> W;
    protected Map<Integer,double[]> W_V;

    protected int featuresCount;
    protected int V_dim;

    protected double regW;
    protected double regV;

    protected double initDev;
    protected double initMean;

    protected double[] sumElement;
    protected double[] sumSquaredElement;

    protected double learningRate = 0.1;
    protected  int hashingSpace = 65536;

    protected Random rand = new Random();

    private FactorizationMachinesSGD()
    {

    }

    protected FactorizationMachinesSGD(int V_dim,
                                       double regW,
                                       double regV,
                                       double initMean,
                                       double initDev,
                                       double learningRate,
                                       int hashingSpace,
                                       String[] columns) throws IOException
    {
        this.V_dim = V_dim;

        this.regW = regW;
        this.regV = regV;
        this.initMean = initMean;
        this.initDev = initDev;

        this.learningRate = learningRate;
        this.hashingSpace = hashingSpace;
        this.featuresCount = columns.length;

        this.W = new HashMap<Integer, Double>(featuresCount*2);
        //hashingSpace*100/75
        int capacity = hashingSpace*2;
        this.W_V = new ConcurrentHashMap<Integer,double[]>(capacity);

        this.sumElement = new double[V_dim];
        this.sumSquaredElement = new double[V_dim];

    }

    private static final long serialVersionUID = "dm.ml.Models.FactorizationMachinesSGD".hashCode();
    private static FactorizationMachinesSGD instance;

    public static synchronized FactorizationMachinesSGD getInstance(String fileName) throws Exception
    {
        if(instance==null)
            instance = loadModel(fileName);
        return instance;
    }

    public static synchronized FactorizationMachinesSGD getInstance(int V_dim,
                                                                    double regW,
                                                                    double regV,
                                                                    double initMean,
                                                                    double initDev,
                                                                    double learningRate,
                                                                    int hashingSpace,
                                                                    String[] columns) throws IOException
    {
        if(instance==null)
            instance = new FactorizationMachinesSGD(V_dim,
                                                    regW,
                                                    regV,
                                                    initMean,
                                                    initDev,
                                                    learningRate,
                                                    hashingSpace,
                                                    columns);
        return instance;
    }


    @Override
    public double predict(Map<String, String> X)
    {
        int[] indices = hash(X);
        return predict(indices);
    }

    public double predict(int[] indices)
    {
        double WtX = 0;

        for (int i = 0; i < indices.length; i++)
        {
            int index = indices[i];
            double wi = W.getOrDefault(index,getDefaultW());
            WtX += wi;
            W.put(index,wi);
        }

        for (int f = 0; f < V_dim; f++)
        {
            sumElement[f] = 0;
            sumSquaredElement[f] = 0;
        }

        for (int i = 0; i < indices.length; i++)
        {
            int index = indices[i];
            initWVi(index);
            double[] vi = W_V.get(index);

            for (int f = 0; f < V_dim; f++)
            {
                double x = vi[f];
                sumElement[f] = sumElement[f]+x;
                sumSquaredElement[f] = sumSquaredElement[f]+x*x;
            }
        }

        for (int f = 0; f < V_dim; f++)
        {
            WtX += 0.5 * (sumElement[f]* sumElement[f] - sumSquaredElement[f]);
        }

        return dm.utils.Functions.sigmoid(WtX);
    }


    @Override
    public double fit(Map<String, String> X, double y)
    {
        int[] indices = hash(X);
        double p = predict(indices);

        //reference -- Factorization Machines with libFM
        double sigmoid = 1.0/(1.0+Math.exp(-y*p));
        double multiplier = y*(sigmoid-1.0);

        //bias and first order
        for (int i = 0; i < indices.length; i++)
        {
            int index=indices[i];

            double defaultW = getDefaultW();
            double wi = W.getOrDefault(index,defaultW);
            double gradient_wi = 1.0;

            wi = wi - learningRate * (multiplier*gradient_wi + regW * wi);
            W.put(index,wi);
        }

        //second order
        for (int i = 0; i < indices.length; i++)
        {
            int index = indices[i];
            initWVi(index);
            double[] vi = W_V.get(index);

            for (int f = 0; f < V_dim; f++)
            {
                //grad = sumElement[f] * x - v * x * x;
                //grand = sumElement[f] - v;
                double vif = vi[f];
                double gradient_vif = sumElement[f] - vif;

                vif = vif - learningRate * (multiplier * gradient_vif  + regV * vif);
                vi[f] = vif;
            }
        }

        return p;
    }

    private double getDefaultW() {
        return initMean+initDev*rand.nextGaussian();
    }


    protected void initWVi(int index)
    {

        if(!W_V.keySet().contains(index))
        {
            W_V.put(index,new double[V_dim]);

            for (int k = 0; k < V_dim; k++)
            {
                W_V.get(index)[k] = initMean+rand.nextGaussian()*initDev;
            }
        }
    }

    protected int[] hash(Map<String,String> X)
    {
        //List<Integer> indices = new ArrayList<Integer>(featuresCount);
        // plus 1 to add bias term
        int[] indices = new int[featuresCount+1];
        int index = 0;
        //bias term
        indices[index]=0;

        for(Entry<String, String> key_value_pair : X.entrySet())
        {
            index++;
            int h = Math.abs((key_value_pair.getKey()+"_"+key_value_pair.getValue()).hashCode());
            // hashingSpace must be a non-zero power of 2
            // 1 is added to hash index because I want 0 to indicate the bias term.
            indices[index] = (h & (hashingSpace-1)) + 1;
        }

        return indices;
    }


    public static double logLoss(double p,double y)
    {
        p = Math.max(Math.min(p, 1. - 10e-15), 10e-15);
        if(y>=1.0)
        {
            return -Math.log(p);
        }
        else
        {
            return -Math.log(1. - p);
        }
    }


    public void saveModel(String fileName) throws Exception
    {

        FileOutputStream fos = null;
        DataOutputStream dos = null;
        ObjectOutputStream oos = null;
        try
        {
            fos = new FileOutputStream(fileName);
            dos = new DataOutputStream(fos);
            oos = new ObjectOutputStream(fos);

            dos.writeInt(V_dim);

            dos.writeDouble(regW);
            dos.writeDouble(regV);
            dos.writeDouble(initMean);
            dos.writeDouble(initDev);

            dos.writeDouble(learningRate);
            dos.writeInt(hashingSpace);
            dos.writeInt(featuresCount);

            dos.flush();

            oos.writeObject(W);
            oos.writeObject(W_V);

            oos.flush();
        }
        catch(Exception e) {
            throw e;
        } finally {
            if(dos!=null)
                dos.close();
            if(fos!=null)
                fos.close();
            if(oos!=null) {
                oos.close();
            }
        }
    }


    protected static FactorizationMachinesSGD loadModel(String fileName) throws Exception
    {
        InputStream is = null;
        DataInputStream dis = null;
        ObjectInputStream ois = null;
        FactorizationMachinesSGD fm = new FactorizationMachinesSGD();

        try {
            is = new FileInputStream(fileName);
            dis = new DataInputStream(is);
            ois = new ObjectInputStream(is);

            fm.V_dim = dis.readInt();

            fm.regW = dis.readDouble();
            fm.regV = dis.readDouble();
            fm.initMean = dis.readDouble();
            fm.initDev = dis.readDouble();

            fm.learningRate = dis.readDouble();
            fm.hashingSpace = dis.readInt();
            fm.featuresCount = dis.readInt();

            fm.W = (ConcurrentHashMap<Integer,Double>)ois.readObject();
            fm.W_V = (ConcurrentHashMap<Integer,double[]>)ois.readObject();

            return fm;
        }
        catch(Exception e)
        {
            throw e;
        } finally {
            if(dis!=null)
                dis.close();
            if(is!=null)
                is.close();
        }
    }


}
