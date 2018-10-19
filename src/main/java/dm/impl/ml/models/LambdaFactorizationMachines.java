package dm.impl.ml.models;

import dm.interfaces.Pairwise;
import dm.utils.Pair;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LambdaFactorizationMachines extends FactorizationMachines implements Pairwise
{

    private LambdaFactorizationMachines() throws IOException
    {
    }

    private LambdaFactorizationMachines(double alpha,
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
        this.alpha = alpha;
        this.beta = beta;
        this.L1 = L1;
        this.L2 = L2;

        this.alpha_V = alpha_fm;
        this.beta_V = beta_fm;
        this.L1_V = L1_fm;
        this.L2_V = L2_fm;

        this.V_dim = fm_dim;
        this.fm_initDev = fm_initDev;
        this.dropoutRate = dropoutRate;

        this.hashingSpace = hashingSpace;
        this.N = new double[hashingSpace];
        this.Z = new double[hashingSpace];

        this.featuresCount = columns.length;
        this.W = new HashMap<Integer, Double>(featuresCount*2);

        //hashingSpace*100/75
        int capacity = hashingSpace*3/2;

        /*
        this.Z_V = new HashMap<Integer,double[]>(capacity);
        this.N_V = new HashMap<Integer,double[]>(capacity);
        this.W_V = new HashMap<Integer,double[]>(capacity);
        */

        this.Z_V = new ConcurrentHashMap<Integer,double[]>(capacity);
        this.N_V = new ConcurrentHashMap<Integer,double[]>(capacity);
        this.W_V = new ConcurrentHashMap<Integer,double[]>(capacity);

    }

    private static final long serialVersionUID = "dm.ml.Models.LambdaFactorizationMachines".hashCode();
    private static LambdaFactorizationMachines instance;

    public static synchronized LambdaFactorizationMachines getInstance(String fileName) throws Exception
    {
        if(instance==null)
            instance = loadModel(fileName);
        return instance;
    }

    public static synchronized LambdaFactorizationMachines getInstance(double alpha,
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
            instance = new LambdaFactorizationMachines(alpha, beta, L1, L2,
                    alpha_fm,beta_fm,L1_fm,L2_fm,
                    fm_dim,fm_initDev,dropoutRate,
                    hashingSpace, columns);
        return instance;
    }



    @Override
    public void fit( List<Pair<Map<String,String>,Double>> samples)
    {
        for(int i = 0; i < samples.size(); ++i)
        {
            for(int j = i + 1; j < samples.size(); ++j)
            {
                double left = samples.get(i).getRight();
                double right = samples.get(j).getRight();

                if(left==right)
                {
                    continue;
                }
                else if(left > right)
                {
                    fit(samples.get(i).getLeft(),samples.get(j).getLeft());
                }
                else
                {
                    fit(samples.get(j).getLeft(),samples.get(i).getLeft());
                }
            }
        }
    }

    @Override
    protected double predict(int[] indices)
    {
        double wTx = 0.0;
        int len_X = indices.length;
        int index = 0;
        double weight = 0;

        //calculate the first order contribution.
        for (int i=0;i<len_X;i++)
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
        for (int i=0;i<len_X;i++)
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

        for(int i=0;i<len_X;i++)
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

        wTx = Math.max(Math.min(wTx, 35.), -35.);
        // sigmoid function
        return 1. / (1. + Math.exp(-wTx));

    }

    private void fit(Map<String,String> positiveX,Map<String,String> negativeX)
    {

        int[] indices1 = hash(positiveX);
        int[] indices2 = hash(negativeX);

        HashSet<Integer> thetaIndexSet = new HashSet<>();
        for (int i : indices1) {
            thetaIndexSet.add(i);
        }
        for (int i : indices2) {
            thetaIndexSet.add(i);
        }
        int[] thetaIndicies = new int[thetaIndexSet.size()];
        int index=0;
        for (Integer integer : thetaIndexSet) {
            thetaIndicies[index]=integer;
            index++;
        }
        predictBeforeUpdate(thetaIndicies);

        double p1 = predict(indices1);
        double p2 = predict(indices2);
        double lambda = -1 / (1 + Math.exp(p1 - p2));

        update(indices1,indices2,lambda);
    }

    //no bias term
    protected int[] hash(Map<String,String> X)
    {
        //List<Integer> indices = new ArrayList<Integer>(featuresCount);
        int[] indices = new int[featuresCount];
        int index = 0;

        for(Map.Entry<String, String> key_value_pair : X.entrySet())
        {
            int h = Math.abs((key_value_pair.getKey()+"_"+key_value_pair.getValue()).hashCode());
            // hashingSpace must be a non-zero power of 2
            // 1 is added to hash index because I want 0 to indicate the bias term.
            indices[index] = (h & (hashingSpace-1)) + 1;
            index++;
        }

        return indices;
    }


    protected double predictBeforeUpdate(int[] indices)
    {
        //writeLock.lock();

        double wTx = 0.0;
        int len_X = indices.length;
        int index=0;
        double weight=0;

        //calculate the first order contribution.
        for (int i=0;i<len_X;i++)
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
        for (int i=0;i<len_X;i++)
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

        for(int i=0;i<len_X;i++)
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

        //writeLock.unlock();

        wTx = Math.max(Math.min(wTx, 35.), -35.);
        // sigmoid function
        return 1. / (1. + Math.exp(-wTx));
    }

    //refrence
    //http://castellanzhang.github.io/2017/07/16/lambdafm/
    //
    protected void update(int[] positiveIndices, int[] negativeIndices, double lambda)
    {
        //writeLock.lock();

        HashSet<Integer> thetaPositions = new HashSet<>();

        HashSet<Integer> positiveSet = new HashSet<>();
        for (int positiveIndex : positiveIndices) {
            positiveSet.add(positiveIndex);
            thetaPositions.add(positiveIndex);
        }

        HashSet<Integer> negativeSet = new HashSet<>();
        for (int negativeIndex : negativeIndices) {
            negativeSet.add(negativeIndex);
            thetaPositions.add(negativeIndex);
        }


        //sums for calculating gradients for FM.
        HashMap<Integer,double[]> fm_positive_sum_gradient = new HashMap<Integer, double[]>();
        HashMap<Integer,double[]> fm_negative_sum_gradient = new HashMap<Integer, double[]>();

        // update the first order weights
        for (Integer thetaIndex : thetaPositions)
        {
            int xi1 = positiveSet.contains(thetaIndex)?1:0;
            if(xi1>0)
            {
                //initialize the sumElement of the FM interaction weights.
                fm_positive_sum_gradient.put(thetaIndex,new double[V_dim]);
            }

            int xi2 = negativeSet.contains(thetaIndex)?1:0;
            if(xi2>0)
            {
                //initialize the sumElement of the FM interaction weights.
                fm_negative_sum_gradient.put(thetaIndex,new double[V_dim]);
            }

            int xi = xi1 - xi2;
            // gradient of w is x,xi - xj = 1.0 for index in positive but not in negative
            if(xi!=0)
            {
                double gradient = lambda * xi;
                double sigma = (Math.sqrt(N[thetaIndex] + gradient * gradient) - Math.sqrt(N[thetaIndex])) / alpha;
                Z[thetaIndex] += gradient - sigma * W.get(thetaIndex);
                N[thetaIndex] += gradient * gradient;
            }
        }


        //sumElement the gradients for FM interaction weights.
        for(int i=0;i<positiveIndices.length;i++)
        {
            int index_i = positiveIndices[i];
            double[] fm_sum_gradient_i = fm_positive_sum_gradient.get(index_i);

            for(int j=0;j<positiveIndices.length;j++)
            {
                if(i != j)
                {
                    int index_j = positiveIndices[j];
                    double[] w_fm_j = W_V.get(index_j);

                    for(int k = 0; k< V_dim; k++)
                    {
                        fm_sum_gradient_i[k] += w_fm_j[k];
                    }
                }
            }
        }

        //sumElement the gradients for FM interaction weights.
        for(int i=0;i<negativeIndices.length;i++)
        {
            int index_i = negativeIndices[i];
            double[] fm_sum_gradient_i = fm_negative_sum_gradient.get(index_i);

            for(int j=0;j<negativeIndices.length;j++)
            {
                if(i != j)
                {
                    int index_j = negativeIndices[j];
                    double[] w_fm_j = W_V.get(index_j);

                    for(int k = 0; k< V_dim; k++)
                    {
                        fm_sum_gradient_i[k] += w_fm_j[k];
                    }
                }
            }
        }

        //update FM interaction weights.
        for (Integer thetaIndex : thetaPositions)
        {
            int xi1 = positiveSet.contains(thetaIndex)?1:0;
            int xi2 = negativeSet.contains(thetaIndex)?1:0;

            double[] z_fm_i = Z_V.get(thetaIndex);
            double[] n_fm_i = N_V.get(thetaIndex);
            double[] w_fm_i = W_V.get(thetaIndex);

            if(xi1>0 && xi2>0)
            {
                double[] fm_sum_p = fm_positive_sum_gradient.get(thetaIndex);
                double[] fm_sum_n = fm_negative_sum_gradient.get(thetaIndex);

                for(int k = 0; k< V_dim; k++)
                {
                    //gradient = lambda12 * ((sum1[f] * xi1 - vif * xi1 * xi1) - (sum2[f] * xi2 - vif * xi2 * xi2));

                    double gradient = lambda * ( fm_sum_p[k]*xi1 - fm_sum_n[k]*xi2);
                    double sigma = (Math.sqrt(n_fm_i[k] + gradient * gradient) - Math.sqrt(n_fm_i[k])) / alpha_V;

                    z_fm_i[k] += gradient - sigma * w_fm_i[k];
                    n_fm_i[k] += gradient * gradient;
                }
            }
            else if(xi1>0)
            {
                double[] fm_sum_p = fm_positive_sum_gradient.get(thetaIndex);

                for(int k = 0; k< V_dim; k++)
                {
                    //gradient = lambda12 * ((sum1[f] * xi1 - vif * xi1 * xi1) - (sum2[f] * xi2 - vif * xi2 * xi2));

                    double gradient = lambda * ( fm_sum_p[k]*xi1);
                    double sigma = (Math.sqrt(n_fm_i[k] + gradient * gradient) - Math.sqrt(n_fm_i[k])) / alpha_V;

                    z_fm_i[k] += gradient - sigma * w_fm_i[k];
                    n_fm_i[k] += gradient * gradient;
                }
            }
            else if(xi2>0)
            {
                double[] fm_sum_n = fm_negative_sum_gradient.get(thetaIndex);

                for(int k = 0; k< V_dim; k++)
                {
                    //gradient = lambda12 * ((sum1[f] * xi1 - vif * xi1 * xi1) - (sum2[f] * xi2 - vif * xi2 * xi2));
                    double gradient = lambda * ( - fm_sum_n[k]*xi2);
                    double sigma = (Math.sqrt(n_fm_i[k] + gradient * gradient) - Math.sqrt(n_fm_i[k])) / alpha_V;

                    z_fm_i[k] += gradient - sigma * w_fm_i[k];
                    n_fm_i[k] += gradient * gradient;
                }
            }
        }

        //writeLock.unlock();
    }

    protected static LambdaFactorizationMachines loadModel(String fileName) throws Exception
    {
        InputStream is = null;
        DataInputStream dis = null;
        ObjectInputStream ois = null;
        LambdaFactorizationMachines fm = new LambdaFactorizationMachines();

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

            fm.Z = new double[fm.hashingSpace];
            fm.N = new double[fm.hashingSpace];

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
