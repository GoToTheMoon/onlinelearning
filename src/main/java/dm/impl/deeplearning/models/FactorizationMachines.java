package dm.impl.deeplearning.models;

import dm.interfaces.FTRL;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class FactorizationMachines implements FTRL,Serializable
{
    private double alpha;
    private double beta;
    private double L1;
    private double L2;

    private double alpha_fm;
    private double beta_fm;
    private double L1_fm;
    private double L2_fm;

    int fm_dim;
    private double fm_initDev;
    private double dropoutRate;
    private int hashingSpace;

    // weights for bias and first order
    private double[] Z;
    // learning rates for bias and first order
    private double[] N;
    // weights for bias and first order in one iteration
    private Map<Integer,Double> W;
    private Map<Integer,double[]> Z_fm;
    private Map<Integer,double[]> N_fm;
    Map<Integer,double[]> W_fm;

    private Random rand = new Random();

    int featuresCount;

    private ReentrantReadWriteLock rrwLock = new ReentrantReadWriteLock();
    private Lock readLock = rrwLock.readLock();
    private Lock writeLock = rrwLock.writeLock();


    FactorizationMachines()
    {
    }


    private FactorizationMachines(double alpha,
                                  double beta,
                                  double L1,
                                  double L2,
                                  double alpha_fm,
                                  double beta_fm,
                                  double L1_fm,
                                  double L2_fm,
                                  int fm_dim,
                                  double fm_initDev,
                                  double dropoutRate,
                                  int hashingSpace,
                                  String[] columns)
    {
        this.alpha = alpha;
        this.beta = beta;
        this.L1 = L1;
        this.L2 = L2;

        this.alpha_fm = alpha_fm;
        this.beta_fm = beta_fm;
        this.L1_fm = L1_fm;
        this.L2_fm = L2_fm;

        this.fm_dim = fm_dim;
        this.fm_initDev = fm_initDev;
        this.dropoutRate = dropoutRate;

        this.hashingSpace = hashingSpace;
        this.N = new double[hashingSpace+1];
        this.Z = new double[hashingSpace+1];

        this.featuresCount = columns.length;
        this.W = new HashMap<Integer, Double>(featuresCount*2);

        //hashingSpace*100/75
        int capacity = hashingSpace*3/2;

        /*
        this.Z_V = new HashMap<Integer,double[]>(capacity);
        this.N_V = new HashMap<Integer,double[]>(capacity);
        this.W_V = new HashMap<Integer,double[]>(capacity);
        */

        this.Z_fm = new HashMap<Integer, double[]>(capacity);
        this.N_fm = new HashMap<Integer,double[]>(capacity);
        this.W_fm = new HashMap<Integer,double[]>(capacity);

    }

    private static final long serialVersionUID = "dm.ml.Models.dm.models.model.FactorizationMachines".hashCode();
    private static FactorizationMachines instance;

    public static synchronized FactorizationMachines getInstance(String fileName) throws Exception
    {
        if(instance==null)
            instance = loadModel(fileName);
        return instance;
    }

    public static synchronized FactorizationMachines getInstance(double alpha,
                                                                 double beta,
                                                                 double L1,
                                                                 double L2,
                                                                 double alpha_fm,
                                                                 double beta_fm,
                                                                 double L1_fm,
                                                                 double L2_fm,
                                                                 int fm_dim,
                                                                 double fm_initDev,
                                                                 double dropoutRate,
                                                                 int hashingSpace,
                                                                 String[] columns)
    {
        if(instance==null)
            instance = new FactorizationMachines(alpha, beta, L1, L2,
                                                alpha_fm,beta_fm,L1_fm,L2_fm,
                                                fm_dim,fm_initDev,dropoutRate,
                                                hashingSpace, columns);
        return instance;
    }


    @Override
    public double predict(Map<String, String> X) {

        int[] indices = hash(X);
        return predict(indices);
    }

    @Override
    public double fit(Map<String, String> X, double y) {

        int[] indices = hash(X);
        double p = predictBeforeUpdate(indices);
        update(indices,p,y);
        return p;
    }


    private double predict(int[] indices)
    {
        double wTx = 0.0;
        int len_X = indices.length;
        //calculate the bias contribution
        //no regularization for bias
        int index=0;
        double weight = (-Z[index]) / ((beta + Math.sqrt(N[index])) / alpha);
        //bias weight
        wTx += weight;

        //calculate the first order contribution.
        for (int i=1;i<len_X;i++)
        {
            index = indices[i];
            double z = Z[index];
            double sign = z < 0. ? -1.0 : 1.0;

            double n = N[index];

            if (sign * z <= L1)
            {
                weight = 0.0;
            }
            else
            {
                weight = (sign * L1 - z) / ((beta + Math.sqrt(n)) / alpha + L2);
            }

            //first order weights
            wTx += weight;
        }


        //why update w_fm_factor
        //calculate factorization machine contribution.
        for (int i=1;i<len_X;i++)
        {
            index = indices[i];
            init_fm(index);

            double[] z_fm_factor = Z_fm.get(index);
            double[] n_fm_factor = N_fm.get(index);
            double[] w_fm_factor = W_fm.get(index);


            for (int k = 0; k < fm_dim; k++)
            {
                double sign = z_fm_factor[k] < 0. ? -1.0 : 1.0;
                if ( sign * z_fm_factor[k] <= L1_fm)
                {
                    weight = 0.0;
                }
                else
                {
                    weight  = (sign * L1_fm - z_fm_factor[k]) / ((beta_fm + Math.sqrt(n_fm_factor[k])) / alpha_fm + L2_fm);
                }

                w_fm_factor[k] = weight;
            }
        }

        for(int i=1;i<len_X;i++)
        {
            for(int j=i+1;j<len_X;j++)
            {
                int index_i = indices[i];
                int index_j = indices[j];

                double[] w_fm_factor_i = W_fm.get(index_i);
                double[] w_fm_factor_j = W_fm.get(index_j);

                for(int k=0;k<fm_dim;k++)
                {
                    //interation features contribution
                    wTx += w_fm_factor_i[k] * w_fm_factor_j[k];
                }
            }
        }

        wTx = Math.max(Math.min(wTx, 35.), -35.);
        // sigmoid function
        return 1. / (1. + Math.exp(-wTx));

    }

    private double predictBeforeUpdate(int[] indices)
    {
        writeLock.lock();

        double wTx = 0.0;
        int len_X = indices.length;
        //calculate the bias contribution
        //no regularization for bias
        int index=0;
        double weight = (- Z[index]) / ((beta + Math.sqrt(N[index])) / alpha);
        //bias weight
        wTx += weight;
        W.put(index,weight);

        //calculate the first order contribution.
        for (int i=1;i<len_X;i++)
        {
            index = indices[i];

            double z = Z[index];
            double sign = z < 0. ? -1.0 : 1.0;
            double n = N[index];

            if (sign * z <= L1)
            {
                weight = 0.0;
            }
            else
            {
                weight = (sign * L1 - z) / ((beta + Math.sqrt(n)) / alpha + L2);
            }

            //first order weights
            wTx += weight;
            W.put(index,weight);
        }


        //why update w_fm_factor
        //calculate factorization machine contribution.
        for (int i=1;i<len_X;i++)
        {
            index = indices[i];
            init_fm(index);

            double[] z_fm_factor = Z_fm.get(index);
            double[] n_fm_factor = N_fm.get(index);
            double[] w_fm_factor = W_fm.get(index);

            for (int k = 0; k < fm_dim; k++)
            {
                double sign = z_fm_factor[k] < 0. ? -1.0 : 1.0;
                if( sign * z_fm_factor[k] <= L1_fm )
                {
                    weight = 0.0;
                }
                else
                {
                    weight  = (sign * L1_fm - z_fm_factor[k]) / ((beta_fm + Math.sqrt(n_fm_factor[k])) / alpha_fm + L2_fm);
                }

                w_fm_factor[k] = weight;
            }
        }

        for(int i=1;i<len_X;i++)
        {
            for(int j=i+1;j<len_X;j++)
            {
                int index_i = indices[i];
                int index_j = indices[j];

                double[] w_fm_factor_i = W_fm.get(index_i);
                double[] w_fm_factor_j = W_fm.get(index_j);

                for(int k=0;k<fm_dim;k++)
                {
                    //interation features contribution
                    wTx += w_fm_factor_i[k] * w_fm_factor_j[k];
                }
            }
        }

        writeLock.unlock();

        wTx = Math.max(Math.min(wTx, 35.), -35.);
        // sigmoid function
        return 1. / (1. + Math.exp(-wTx));
    }


    //
    private void update(int[] indices, double p, double y)
    {
        writeLock.lock();
        double gradient = p - y;

        //update the bias weights.
        int index = 0;
        double sigma = (Math.sqrt(N[index] + gradient * gradient) - Math.sqrt(N[index])) / alpha;
        Z[index] += gradient - sigma * W.get(index);
        N[index] += gradient * gradient;


        int len_X = indices.length;
        //sums for calculating gradients for FM.
        HashMap<Integer,double[]> fm_sum = new HashMap<Integer, double[]>();

        // update the first order weights
        for(int i=1;i<len_X;i++)
        {
            index = indices[i];
            sigma = (Math.sqrt(N[index] + gradient * gradient) - Math.sqrt(N[index])) / alpha;
            Z[index] += gradient - sigma * W.get(index);
            N[index] += gradient * gradient;
            //initialize the sumElement of the FM interaction weights.
            fm_sum.put(index,new double[fm_dim]);
        }


        //sumElement the gradients for FM interaction weights.
        for(int i=1;i<len_X;i++)
        {
            for(int j=1;j<len_X;j++)
            {
                if(i != j)
                {
                    int index_i = indices[i];
                    int index_j = indices[j];
                    double[] fm_sum_factor = fm_sum.get(index_i);
                    double[] w_fm_factor = W_fm.get(index_j);

                    for(int k=0;k<fm_dim;k++)
                    {
                        fm_sum_factor[k] += w_fm_factor[k];
                    }
                }
            }
        }

        //update FM interaction weights.
        for(int i=1;i<len_X;i++)
        {
            index = indices[i];
            double[] z_fm_factor = Z_fm.get(index);
            double[] n_fm_factor = N_fm.get(index);
            double[] w_fm_factor = W_fm.get(index);
            double[] fm_sum_factor = fm_sum.get(index);

            for(int k=0;k<fm_dim;k++)
            {
                double g_fm = gradient * fm_sum_factor[k];
                sigma = (Math.sqrt(n_fm_factor[k] + g_fm * g_fm) - Math.sqrt(n_fm_factor[k])) / alpha_fm;
                z_fm_factor[k] += g_fm - sigma * w_fm_factor[k];
                n_fm_factor[k] += g_fm * g_fm;

            }

        }

        writeLock.unlock();
    }


    int[] hash(Map<String, String> X)
    {
        //List<Integer> indices = new ArrayList<Integer>(featuresCount);
        int[] indices = new int[featuresCount+1];
        int index = 0;
        indices[index]=0;

        for(Entry<String, String> key_value_pair : X.entrySet())
        {
            index++;
            int h = Math.abs((key_value_pair.getKey()+"_"+key_value_pair.getValue()).hashCode());
            //indices.add( Math.abs((key_value_pair.getKey()+"_"+key_value_pair.getValue()).hashCode()) % hashingSpace);
            indices[index] = (h & (hashingSpace-1)) + 1;
        }

        return indices;
    }


    private void init_fm(int index)
    {

        if(!Z_fm.keySet().contains(index))
        {
            Z_fm.put(index,new double[fm_dim]);
            N_fm.put(index,new double[fm_dim]);
            W_fm.put(index,new double[fm_dim]);

            for (int k = 0; k < fm_dim; k++)
            {
                Z_fm.get(index)[k] = rand.nextGaussian()*fm_initDev;
            }
        }
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

            dos.writeDouble(alpha);
            dos.writeDouble(beta);
            dos.writeDouble(L1);
            dos.writeDouble(L2);

            dos.writeDouble(alpha_fm);
            dos.writeDouble(beta_fm);
            dos.writeDouble(L1_fm);
            dos.writeDouble(L2_fm);

            dos.writeInt(fm_dim);
            dos.writeDouble(fm_initDev);
            dos.writeDouble(dropoutRate);

            dos.writeInt(hashingSpace);

            for (int i = 0; i < Z.length; i++) {
                dos.writeDouble(Z[i]);
            }

            for (int i = 0; i < N.length; i++) {
                dos.writeDouble(N[i]);
            }

            dos.writeInt(featuresCount);
            dos.flush();

            //oos.writeObject(W);
            oos.writeObject(Z_fm);
            oos.writeObject(N_fm);
            oos.writeObject(W_fm);

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


    public static FactorizationMachines loadModel(String fileName) throws Exception
    {
        InputStream is = null;
        DataInputStream dis = null;
        ObjectInputStream ois = null;
        FactorizationMachines fm = new FactorizationMachines();

        try {
            is = new FileInputStream(fileName);
            dis = new DataInputStream(is);
            ois = new ObjectInputStream(is);

            fm.alpha = dis.readDouble();
            fm.beta = dis.readDouble();
            fm.L1 = dis.readDouble();
            fm.L2 = dis.readDouble();

            fm.alpha_fm = dis.readDouble();
            fm.beta_fm = dis.readDouble();
            fm.L1_fm = dis.readDouble();
            fm.L2_fm = dis.readDouble();

            fm.fm_dim = dis.readInt();
            fm.fm_initDev = dis.readDouble();
            fm.dropoutRate = dis.readDouble();

            fm.hashingSpace = dis.readInt();

            fm.Z = new double[fm.hashingSpace+1];
            fm.N = new double[fm.hashingSpace+1];

            for(int i=0;i<fm.Z.length;i++)
            {
                fm.Z[i] = dis.readDouble();
            }

            for(int i=0;i<fm.N.length;i++)
            {
                fm.N[i] = dis.readDouble();
            }


            fm.featuresCount = dis.readInt();
            fm.W = new HashMap<Integer, Double>(fm.featuresCount*2);

            /*
            fm.Z_V = (HashMap<Integer,double[]>)ois.readObject();
            fm.N_V = (HashMap<Integer,double[]>)ois.readObject();
            fm.W_V = (HashMap<Integer,double[]>)ois.readObject();
            */

            fm.Z_fm = (HashMap<Integer,double[]>)ois.readObject();
            fm.N_fm = (HashMap<Integer,double[]>)ois.readObject();
            fm.W_fm = (HashMap<Integer,double[]>)ois.readObject();


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
