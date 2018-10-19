package dm.impl.ml.models;

import dm.interfaces.FTRL;
import dm.utils.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CTRLogisticRegression extends LogisticRegression
{
    private static final long serialVersionUID = "dm.ml.Models.CTRLogisticRegression".hashCode();
    private static CTRLogisticRegression instance;

    private CTRLogisticRegression()
    {
        super();
    }

    private CTRLogisticRegression(double alpha,
                                      double beta,
                                      double L1,
                                      double L2,
                                      int hashingSpace,
                                        String[] firstOrderColumns,
                                        String[] interactionColumns)
    {
        super(alpha, beta, L1, L2, hashingSpace, firstOrderColumns,interactionColumns);
    }


    public static synchronized CTRLogisticRegression getInstance(String fileName) throws Exception
    {
        if(instance==null)
            instance = loadModel(fileName);
        return instance;
    }

    public static synchronized CTRLogisticRegression getInstance(double alpha,
                                                                     double beta,
                                                                     double L1,
                                                                     double L2,
                                                                     int hashingSpace,
                                                                    String[] firstOrderColumns,
                                                                    String[] interactionColumns)
    {
        if(instance==null)
            instance = new CTRLogisticRegression(alpha, beta, L1, L2, hashingSpace, firstOrderColumns,interactionColumns);
        return instance;
    }


    @Override
    protected int[] hash(Map<String,String> X)
    {
        return super.hash(X);
    }


    private static CTRLogisticRegression loadModel(String fileName) throws Exception
    {
        InputStream is = null;
        DataInputStream dis = null;
        CTRLogisticRegression ctrlr = new CTRLogisticRegression();

        try {
            is = new FileInputStream(fileName);
            dis = new DataInputStream(is);


            ctrlr.alpha = dis.readDouble();
            ctrlr.beta = dis.readDouble();
            ctrlr.L1 = dis.readDouble();
            ctrlr.L2 = dis.readDouble();
            ctrlr.hashingSpace = dis.readInt();


            ctrlr.Z = new double[ctrlr.hashingSpace];
            ctrlr.N = new double[ctrlr.hashingSpace];

            for(int i=0;i<ctrlr.Z.length;i++)
            {
                ctrlr.Z[i] = dis.readDouble();
            }

            for(int i=0;i<ctrlr.N.length;i++)
            {
                ctrlr.N[i] = dis.readDouble();
            }

            // init W
            ctrlr.W = new HashMap<Integer, Double>();

            ctrlr.featureCombinations = new ArrayList<Pair<String, String>>();
            int combinationsCount = dis.readInt();

            for(int i=0;i<combinationsCount;i++)
            {
                String left = dis.readUTF();
                String right = dis.readUTF();
                ctrlr.featureCombinations.add(new Pair<String,String>(left,right));
            }

            ctrlr.featuresCount = dis.readInt();
            return ctrlr;
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


    public static void main(String[] args) {
        String fileName = args[0];
        try {
            FTRL instance = CTRLogisticRegression.getInstance(fileName);

            Map<String,String> X = new HashMap<String,String>();
            X.put("bklevel","0");
            X.put("nt","wifi");
            X.put("kernel","Webkit");
            X.put("bl","0");
            X.put("dtbrand","Gionee");
            X.put("dtmodel","GN9005");
            X.put("ft","201");
            X.put("dt","p");
            X.put("bt","A");
            X.put("hour","16");
            X.put("pf","A");
            X.put("browser",null);
            X.put("region","9127");
            X.put("categoryid","2402");

            if(instance!=null) {
                double p = instance.predict(X);
                System.out.println(p);
            }

        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
