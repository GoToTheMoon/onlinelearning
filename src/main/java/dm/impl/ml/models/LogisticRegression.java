package dm.impl.ml.models;
import dm.utils.Combination;
import dm.interfaces.FTRL;
import dm.utils.Pair;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public abstract class LogisticRegression implements FTRL,Serializable
{

    protected double alpha;
    protected double beta;
    protected double L1;
    protected double L2;
    protected int hashingSpace;

    // weights
    protected double[] Z;
    // learning rates
    protected double[] N;
    // lazy weights
    protected Map<Integer,Double> W;

    //
    protected List<Pair<String,String>> featureCombinations;
    //number of single feature and combined featuresCount and bias
    protected int featuresCount;


    protected ReentrantReadWriteLock rrwLock = new ReentrantReadWriteLock();
    protected Lock readLock = rrwLock.readLock();
    protected Lock writeLock = rrwLock.writeLock();

    protected LogisticRegression()
    {
    }

    protected LogisticRegression(double alpha,
                                        double beta,
                                        double L1,
                                        double L2,
                                        int hashingSpace,
                                        String[] firstOrderColumns,
                                        String[] interactionColumns)
    {
        this.alpha = alpha;
        this.beta = beta;
        this.L1 = L1;
        this.L2 = L2;
        this.hashingSpace = hashingSpace;

        this.N = new double[hashingSpace];
        this.Z = new double[hashingSpace];
        this.W = new HashMap<Integer, Double>();

        this.featureCombinations = Combination.combine(interactionColumns,2);
        this.featuresCount = firstOrderColumns.length+featureCombinations.size()+1;

    }


    @Override
    public final double predict(Map<String, String> X) {
        int[] indices = hash(X);
        return predict(indices);
    }


    @Override
    public final double fit(Map<String, String> X, double y)
    {
        int[] indices = hash(X);
        double p = predictBeforeUpdate(indices);
        update(indices,p,y);
        return p;
    }


    private final double predict(int[] indices)
    {
        double wTx = 0.0;

        for (int i=0;i<indices.length;i++)
        {
            int index = indices[i];

            double z = Z[index];
            double n = N[index];

            double sign_z = sign(z);
            double weight = 0.0;

            if (sign_z * z <= L1)
            {
                weight = 0.0;
            }
            else
            {
                weight = (sign_z * L1 - z) / ((beta + Math.sqrt(n)) / alpha + L2);
            }

            wTx += weight;
        }

        wTx = Math.max(Math.min(wTx, 35.), -35.);
        // sigmoid function
        return 1. / (1. + Math.exp(-wTx));
    }

    private final double predictBeforeUpdate(int[] indices)
    {
        writeLock.lock();

        W.clear();
        double wTx = 0.0;

        for (int i=0;i<indices.length;i++)
        {
            int index = indices[i];
            double sign_Z = sign(Z[index]);
            double weight = 0.0;

            if (sign_Z * Z[index] <= L1)
            {
                weight = 0.0;
            }
            else
            {
                weight = (sign_Z * L1 - Z[index]) / ((beta + Math.sqrt(N[index])) / alpha + L2);
            }

            W.put(index, weight);
            wTx += weight;
        }

        writeLock.unlock();

        wTx = Math.max(Math.min(wTx, 35.), -35.);
        // sigmoid function
        return 1. / (1. + Math.exp(-wTx));
    }


    //
    private final void update(int[] indices, double p, double y)
    {
        writeLock.lock();
        double gradient = p - y;

        for (int i=0;i<indices.length;i++)
        {
            int index = indices[i];
            double sigma = (Math.sqrt(N[index] + gradient * gradient) - Math.sqrt(N[index])) / alpha;

            Z[index] += gradient - sigma * W.get(index);
            N[index] += gradient * gradient;
        }
        writeLock.unlock();
    }


    protected int[] hash(Map<String,String> X)
    {
        //List<Integer> indices = new ArrayList<Integer>(featuresCount);
        //indices.add(0);

        int[] indices = new int[featuresCount];
        int index = 0;
        indices[index]=0;

        for(Entry<String, String> key_value_pair : X.entrySet())
        {
            index++;
            //indices[index]=Math.abs((key_value_pair.getKey()+"_"+key_value_pair.getValue()).hashCode()) % hashingSpace;
            indices[index]=Math.floorMod(Math.abs((key_value_pair.getKey()+"_"+key_value_pair.getValue()).hashCode()),
                                         hashingSpace);
        }

        for (int i = 0; i< featureCombinations.size(); i++)
        {
            Pair<String,String> comb = featureCombinations.get(i);
            String left = comb.getLeft();
            String right = comb.getRight();

            //indices[index]=Math.abs((left+"_"+right+X.get(left)+X.get(right)).hashCode()) % hashingSpace;
            index++;
            indices[index]=Math.floorMod(Math.abs((left+"_"+right+X.get(left)+X.get(right)).hashCode()),
                                         hashingSpace);
        }

        return indices;
    }

    private double sign(double x)
    {
        if (x > 0) {
            return 1.0;
        } else if (x < 0) {
            return -1.0;
        } else {
            return 0.0;
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
        try
        {
            fos = new FileOutputStream(fileName);
            dos = new DataOutputStream(fos);

            dos.writeDouble(alpha);
            dos.writeDouble(beta);
            dos.writeDouble(L1);
            dos.writeDouble(L2);
            dos.writeInt(hashingSpace);

            for (int i = 0; i < Z.length; i++) {
                dos.writeDouble(Z[i]);
            }

            for (int i = 0; i < N.length; i++) {
                dos.writeDouble(N[i]);
            }

            //W donot need to save

            // save combinations count
            dos.writeInt(featureCombinations.size());

            for (int i = 0; i < featureCombinations.size(); i++)
            {
                dos.writeUTF(featureCombinations.get(i).getLeft());
                dos.writeUTF(featureCombinations.get(i).getRight());
            }

            dos.writeInt(featuresCount);

            dos.flush();
        }
        catch(Exception e) {
            throw e;
        } finally {
            if(dos!=null)
                dos.close();
            if(fos!=null)
                fos.close();
        }
    }
}
