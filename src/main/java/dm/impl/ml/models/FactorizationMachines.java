package dm.impl.ml.models;
import dm.interfaces.FTRL;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Random;


public class FactorizationMachines implements FTRL,Serializable
{
    protected double alpha;
    protected double beta;
    protected double L1;
    protected double L2;

    protected double alpha_V;
    protected double beta_V;
    protected double L1_V;
    protected double L2_V;

    protected int V_dim;
    protected double fm_initDev;
    protected double dropoutRate;
    protected int hashingSpace;
    protected int weightsCount;

    // weights for bias and first order in one iteration
    protected Map<Integer,Double> W;
    protected Map<Integer,double[]> W_V;

    // weights for bias and first order
    protected double[] Z;
    // learning rates for bias and first order
    protected double[] N;

    protected Map<Integer,double[]> Z_V;
    protected Map<Integer,double[]> N_V;


    protected Random rand = new Random();

    protected int featuresCount;

    protected ReentrantReadWriteLock rrwLock = new ReentrantReadWriteLock();
    protected Lock readLock = rrwLock.readLock();
    protected Lock writeLock = rrwLock.writeLock();


    protected FactorizationMachines() throws IOException
    {
    }


    protected FactorizationMachines(double alpha,
                                  double beta,
                                  double L1,
                                  double L2,
                                  double alpha_V,
                                  double beta_V,
                                  double L1_V,
                                  double L2_V,
                                  int V_dim,
                                  double fm_initDev,
                                  double dropoutRate,
                                  int hashingSpace,
                                  String[] columns) throws IOException
    {
        init(alpha, beta, L1, L2,
                alpha_V, beta_V, L1_V, L2_V,
                V_dim, fm_initDev, dropoutRate, hashingSpace,
                columns);

    }

    protected void init(double alpha, double beta, double L1, double L2,
                      double alpha_V, double beta_V, double L1_V, double L2_V,
                      int V_dim, double fm_initDev, double dropoutRate, int hashingSpace,
                      String[] columns) {
        this.alpha = alpha;
        this.beta = beta;
        this.L1 = L1;
        this.L2 = L2;

        this.alpha_V = alpha_V;
        this.beta_V = beta_V;
        this.L1_V = L1_V;
        this.L2_V = L2_V;

        this.V_dim = V_dim;
        this.fm_initDev = fm_initDev;
        this.dropoutRate = dropoutRate;

        this.hashingSpace = hashingSpace;
        this.weightsCount = hashingSpace+1;

        this.N = new double[weightsCount];
        this.Z = new double[weightsCount];

        this.featuresCount = columns.length;
        this.W = new HashMap<Integer, Double>(featuresCount*2);

        //hashingSpace*100/75
        int capacity = hashingSpace*2;

        /*
        this.Z_V = new HashMap<Integer,double[]>(capacity);
        this.N_V = new HashMap<Integer,double[]>(capacity);
        this.W_V = new HashMap<Integer,double[]>(capacity);
        */

        this.Z_V = new ConcurrentHashMap<Integer, double[]>(capacity);
        this.N_V = new ConcurrentHashMap<Integer,double[]>(capacity);
        this.W_V = new ConcurrentHashMap<Integer,double[]>(capacity);
    }

    private static final long serialVersionUID = "dm.ml.Models.FactorizationMachines".hashCode();
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
                                                                 String[] columns) throws IOException
    {
        if(instance==null)
            instance = new FactorizationMachines(alpha, beta, L1, L2,
                                                alpha_fm,beta_fm,L1_fm,L2_fm,
                                                fm_dim,fm_initDev,dropoutRate,
                                                hashingSpace, columns);
        return instance;
    }



    @Override
    public double predict(Map<String, String> X)
    {
        int[] indices = hash(X);
        return predict(indices);
    }


    @Override
    public double fit(Map<String, String> X, double y)
    {
        int[] indices = hash(X);
        double p = predictBeforeUpdate(indices);
        update(indices,p,y);
        return p;
    }


    protected double predict(int[] indices)
    {
        double wTx = 0.0;
        int len_X = indices.length;

        //calculate the bias contribution
        //no regularization for bias
        int index=0;
        double weight = (-Z[index]) / ((beta + Math.sqrt(N[index])) / alpha);
        //bias term weight
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

            double[] z_fm_factor = Z_V.get(index);
            double[] n_fm_factor = N_V.get(index);
            double[] w_fm_factor = W_V.get(index);


            for (int k = 0; k < V_dim; k++)
            {
                double sign = z_fm_factor[k] < 0. ? -1.0 : 1.0;
                if ( sign * z_fm_factor[k] <= L1_V)
                {
                    weight = 0.0;
                }
                else
                {
                    weight  = (sign * L1_V - z_fm_factor[k]) / ((beta_V + Math.sqrt(n_fm_factor[k])) / alpha_V + L2_V);
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

                double[] w_fm_factor_i = W_V.get(index_i);
                double[] w_fm_factor_j = W_V.get(index_j);


                for(int k = 0; k< V_dim; k++)
                {
                    //interation features contribution
                    wTx += w_fm_factor_i[k] * w_fm_factor_j[k];
                }
            }
        }

        // sigmoid function
        return dm.utils.Functions.sigmoid(wTx);

    }



    protected double predictBeforeUpdate(int[] indices)
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

            double[] z_fm_factor = Z_V.get(index);
            double[] n_fm_factor = N_V.get(index);
            double[] w_fm_factor = W_V.get(index);


            for (int k = 0; k < V_dim; k++)
            {
                double sign = z_fm_factor[k] < 0. ? -1.0 : 1.0;
                if( sign * z_fm_factor[k] <= L1_V)
                {
                    weight = 0.0;
                }
                else
                {
                    weight  = (sign * L1_V - z_fm_factor[k]) / ((beta_V + Math.sqrt(n_fm_factor[k])) / alpha_V + L2_V);
                }

                w_fm_factor[k] = weight;
            }
        }

        for(int i=1;i<len_X;i++)
        {
            int index_i = indices[i];
            double[] w_fm_i = W_V.get(index_i);

            for(int j=i+1;j<len_X;j++)
            {
                int index_j = indices[j];
                double[] w_fm_j = W_V.get(index_j);

                for(int k = 0; k< V_dim; k++)
                {
                    //interation features contribution
                    wTx += w_fm_i[k] * w_fm_j[k];
                }
            }
        }

        writeLock.unlock();

        // sigmoid function
        return dm.utils.Functions.sigmoid(wTx);
    }

    //
    protected void update(int[] indices, double p, double y)
    {
        writeLock.lock();
        double gradient = p - y;

        //update the bias term weight
        int index = 0;
        double sigma = (Math.sqrt(N[index] + gradient * gradient) - Math.sqrt(N[index])) / alpha;
        Z[index] += gradient - sigma * W.get(index);
        N[index] += gradient * gradient;


        int len_X = indices.length;
        //sums for calculating gradients for FM.
        HashMap<Integer,double[]> fm_sum_gradient = new HashMap<Integer, double[]>();

        // update the first order weights
        for(int i=1;i<len_X;i++)
        {
            index = indices[i];
            double gradient_first_order = gradient;
            sigma = (Math.sqrt(N[index] + gradient_first_order * gradient_first_order) - Math.sqrt(N[index])) / alpha;
            Z[index] += gradient_first_order - sigma * W.get(index);
            N[index] += gradient_first_order * gradient_first_order;
            //initialize the sumElement of the FM interaction weights.
            fm_sum_gradient.put(index,new double[V_dim]);
        }

        //sumElement the gradients for FM interaction weights.
        for(int i=1;i<len_X;i++)
        {
            int index_i = indices[i];
            double[] fm_sum_gradient_i = fm_sum_gradient.get(index_i);

            for(int j=1;j<len_X;j++)
            {
                if(i != j)
                {
                    int index_j = indices[j];
                    double[] w_fm_j = W_V.get(index_j);

                    for(int k = 0; k< V_dim; k++)
                    {
                        //sumElement(Wj)
                        fm_sum_gradient_i[k] += w_fm_j[k];
                    }
                }
            }
        }

        //update FM interaction weights.
        for(int i=1;i<len_X;i++)
        {
            index = indices[i];
            double[] z_fm_factor = Z_V.get(index);
            double[] n_fm_factor = N_V.get(index);
            double[] w_fm_factor = W_V.get(index);
            double[] fm_sum_gradient_i = fm_sum_gradient.get(index);

            for(int k = 0; k< V_dim; k++)
            {
                double gradient_second_order = gradient * fm_sum_gradient_i[k];
                sigma = (Math.sqrt(n_fm_factor[k] + gradient_second_order * gradient_second_order) - Math.sqrt(n_fm_factor[k])) / alpha_V;
                z_fm_factor[k] += gradient_second_order - sigma * w_fm_factor[k];
                n_fm_factor[k] += gradient_second_order * gradient_second_order;
            }
        }

        writeLock.unlock();
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


    protected void init_fm(int index)
    {

        if(!Z_V.keySet().contains(index))
        {
            Z_V.put(index,new double[V_dim]);
            N_V.put(index,new double[V_dim]);
            W_V.put(index,new double[V_dim]);

            for (int k = 0; k < V_dim; k++)
            {
                Z_V.get(index)[k] = rand.nextGaussian()*fm_initDev;
            }
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

            dos.writeDouble(alpha_V);
            dos.writeDouble(beta_V);
            dos.writeDouble(L1_V);
            dos.writeDouble(L2_V);

            dos.writeInt(V_dim);
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
            oos.writeObject(Z_V);
            oos.writeObject(N_V);
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


    protected static FactorizationMachines loadModel(String fileName) throws Exception
    {
        FactorizationMachines fm = new FactorizationMachines();

        updateModelFromFile(fileName, fm);

        return fm;
    }

    protected static void updateModelFromFile(String fileName, FactorizationMachines fm) throws IOException, ClassNotFoundException {
        InputStream is = null;
        DataInputStream dis = null;
        ObjectInputStream ois = null;

        try {
            is = new FileInputStream(fileName);
            dis = new DataInputStream(is);
            ois = new ObjectInputStream(is);

            fm.alpha = dis.readDouble();
            fm.beta = dis.readDouble();
            fm.L1 = dis.readDouble();
            fm.L2 = dis.readDouble();

            fm.alpha_V = dis.readDouble();
            fm.beta_V = dis.readDouble();
            fm.L1_V = dis.readDouble();
            fm.L2_V = dis.readDouble();

            fm.V_dim = dis.readInt();
            fm.fm_initDev = dis.readDouble();
            fm.dropoutRate = dis.readDouble();

            fm.hashingSpace = dis.readInt();
            fm.weightsCount=fm.hashingSpace+1;

            fm.Z = new double[fm.weightsCount];
            fm.N = new double[fm.weightsCount];

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

            fm.Z_V = (ConcurrentHashMap<Integer,double[]>)ois.readObject();
            fm.N_V = (ConcurrentHashMap<Integer,double[]>)ois.readObject();
            fm.W_V = (ConcurrentHashMap<Integer,double[]>)ois.readObject();
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
