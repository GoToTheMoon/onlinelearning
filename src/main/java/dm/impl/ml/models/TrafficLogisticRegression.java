package dm.impl.ml.models;

import dm.interfaces.FTRL;
import dm.utils.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TrafficLogisticRegression extends LogisticRegression
{
    private static final long serialVersionUID = "dm.ml.Models.TrafficLogisticRegression".hashCode();
    private static TrafficLogisticRegression instance;

    private TrafficLogisticRegression()
    {
        super();
    }

    private TrafficLogisticRegression(double alpha,
                                        double beta,
                                        double L1,
                                        double L2,
                                        int hashingSpace,
                                      String[] firstOrderColumns,
                                      String[] interactionColumns)
    {
        super(alpha, beta, L1, L2, hashingSpace, firstOrderColumns,interactionColumns);
    }


    public static synchronized TrafficLogisticRegression getInstance(String fileName) throws Exception
    {
        if(instance==null)
            instance = loadModel(fileName);
        return instance;
    }

    public static synchronized TrafficLogisticRegression getInstance(double alpha,
                                                                    double beta,
                                                                    double L1,
                                                                    double L2,
                                                                    int hashingSpace,
                                                                    String[] firstOrderColumns,
                                                                    String[] interactionColumns)
    {
        if(instance==null)
            instance = new TrafficLogisticRegression(alpha, beta, L1, L2, hashingSpace, firstOrderColumns,interactionColumns);
        return instance;
    }


    @Override
    protected int[] hash(Map<String,String> X)
    {
        return super.hash(X);
    }


    private static TrafficLogisticRegression loadModel(String fileName) throws Exception
    {
        InputStream is = null;
        DataInputStream dis = null;
        TrafficLogisticRegression tlr = new TrafficLogisticRegression();

        try {
            is = new FileInputStream(fileName);
            dis = new DataInputStream(is);


            tlr.alpha = dis.readDouble();
            tlr.beta = dis.readDouble();
            tlr.L1 = dis.readDouble();
            tlr.L2 = dis.readDouble();
            tlr.hashingSpace = dis.readInt();


            tlr.Z = new double[tlr.hashingSpace];
            tlr.N = new double[tlr.hashingSpace];

            for(int i=0;i<tlr.Z.length;i++)
            {
                tlr.Z[i] = dis.readDouble();
            }

            for(int i=0;i<tlr.N.length;i++)
            {
                tlr.N[i] = dis.readDouble();
            }

            // init W
            tlr.W = new HashMap<Integer, Double>();

            tlr.featureCombinations = new ArrayList<Pair<String, String>>();
            int combinationsCount = dis.readInt();

            for(int i=0;i<combinationsCount;i++)
            {
                String left = dis.readUTF();
                String right = dis.readUTF();
                tlr.featureCombinations.add(new Pair<String,String>(left,right));
            }

            tlr.featuresCount = dis.readInt();
            return tlr;
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

        try {
            //String fileName = args[0];
            //FTRL instance = TrafficLogisticRegression.getInstance(fileName);

            double alpha = 0.1;
            double beta = 1.0;
            double L1 = 1.0;
            double L2 = 1.0;
            int hashingSpace = 100000;

            String[] columns = {
                    "channel",
                    "hour",
                    "region",
                    "categoryid",
                    "position",
                    "bklevel",
                    "ft",
                    "min_cpm",
                    "dt",
                    "pf",
                    "nt",
                    "browser",
                    "kernel",
                    "dtbrand",
                    "dtmodel"};


            FTRL instance = TrafficLogisticRegression.getInstance(alpha,beta,L1,L2,hashingSpace,columns,columns);

            Map<String,String> X = new HashMap<String,String>();
            X.put("chanel","BAIDU");
            X.put("hour","16");
            X.put("region","9127");
            X.put("categoryid","2402");
            X.put("position","0");
            X.put("bklevel","0");
            X.put("ft","201");
            X.put("min_cpm","200000");
            X.put("dt","p");
            X.put("pf","A");
            X.put("nt","wifi");
            X.put("browser",null);
            X.put("kernel","Webkit");
            X.put("dtbrand","Gionee");
            X.put("dtmodel","GN9005");

            if(instance!=null) {
                double y = 1;
                instance.fit(X,y);
                instance.fit(X,y);
                double p = instance.predict(X);
                System.out.println(p);
            }

        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
