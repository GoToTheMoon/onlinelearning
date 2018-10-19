package dm.impl.deeplearning.models;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import dm.interfaces.FNNInterface;

public class FNN implements FNNInterface {
    private static String[] data_columns = {
            "site",
            "categoryid",
            "region",
            "width",
            "height",
            "stype",
            "position",
            "bkid",
            "mid",
            "dt",
            "pf",
            "min_cpm",
            "bklevel",
            "plantype",
            "grouptype",
            "nt",
            "browser",
            "kernel",
            "dtbrand",
            "dtmodel",
            "dayofweek",
            "hour",
            "channel",
            "cid",
            "adgroupid",
            "planid",
            "svs",
            "base_price"
    };
    public static BpDeep bp;
    public static FactorizationMachines fm;
    public static ruledeep rbp;
    public static MLP mlp;
    public static Dropout dnp;
    private static FNN instance;
    public static synchronized FNN getInstance(String path) throws Exception
    {
        if(instance==null)
            instance = loadmodel(path);
        return instance;
    }
    public static void fmtrain(
                           int factors,String fileName) throws Exception {


        double alpha = 0.01;
        double beta = 1.0;
        double L1 = 0.1;
        double L2 = 1.0;
        double alpha_fm = 0.01;
        double beta_fm = 1.0;
        double L1_fm = 0.1;
        double L2_fm = 1.0;
        double fm_initDev = 1.0;
        double dropoutRate = 0.001;
        int hashingSpace = 1048576;

        FactorizationMachines fm = FactorizationMachines.getInstance(alpha, beta, L1, L2,
                alpha_fm, beta_fm, L1_fm, L2_fm, factors, fm_initDev, dropoutRate, hashingSpace, data_columns);
        String path = "http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + fileName + "?op=open&user.name=dsp";
        URL url = new URL(path);
        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream());
        BufferedReader br = new BufferedReader(inputStream);
        int length = data_columns.length;
        String value = br.readLine();
        while (null != (value= br.readLine())) {
            String[] values=value.split(",");
            Map<String, String> X = new HashMap<String, String>();
            double y = Double.parseDouble(values[length]);
            for (int i = 0; i < values.length - 1; i++) {
                X.put(data_columns[i], values[i]);
            }
            fm.fit(X, y);
        }
        fm.saveModel("fnn/fm.model");
    }

    public static void updatefm(String fileName) throws Exception {
        FactorizationMachines FMM= FactorizationMachines.getInstance("fnn/fm.model");
        FMM.featuresCount=data_columns.length;
        String path = "http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + fileName + "?op=open&user.name=dsp";
        URL url = new URL(path);
        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream());
        BufferedReader br = new BufferedReader(inputStream);

        String value = br.readLine();
        int length = data_columns.length;

        while ((value = br.readLine()) != null) {
            String[] values=value.split(",");
            Map<String, String> X = new HashMap<String, String>();
            double y = Double.parseDouble(values[values.length-1]);
            for (int i = 0; i < values.length - 1; i++) {
                X.put(data_columns[i], values[i]);
            }
            FMM.fit(X, y);
        }
        FMM.saveModel("fnn/fm.model");
    }

    private static double[] onArrayTogater(double[] aa, double[] bb) {

        if (aa == null) {
            return bb;
        }
        double[] collectionInt = new double[aa.length + bb.length];
        System.arraycopy(aa, 0, collectionInt, 0, aa.length);
        System.arraycopy(bb, 0, collectionInt, aa.length, aa.length + bb.length - aa.length);
        return collectionInt;
    }

    public void fit(String value) {
        int[] label;
        String[] strs = value.split(",");
        int labell=Integer.parseInt(strs[strs.length-1]);
        if (labell==1){
            label= new int[]{0, 1};
        }else{
            label=new int[]{1,0};
        }
        String[] fea= Arrays.copyOfRange(strs,0,strs.length-1);
        Map<Integer, double[]> wfm= fm.W_fm;
        Map<String, String> X = new HashMap<String, String>();
        for (int i = 0; i < fea.length; i++) {
            X.put(FNN.data_columns[i], fea[i]);
        }
        int[] indices = fm.hash(X);
        double[] aa0 = wfm.get(0);
        for (int index : indices) {
            double[] vi;
            if (wfm.containsKey(index)) {
                vi = wfm.get(index);
            } else {
                vi = new double[fm.fm_dim];
            }
            aa0 = onArrayTogater(aa0, vi);
        }
        dnp.fit(aa0, label);
    }

    public static FNN loadmodel(String path) throws Exception {
        Dropout bp1 = Dropout.loadmodel(path+"/bp.model");
        FactorizationMachines FMM= FactorizationMachines.loadModel(path+"/fm.model");
        FNN fnn=new FNN();
        dnp=bp1;
        fm=FMM;
        return fnn;
    }
    public static void updatabp(String filepath) throws Exception {
        ruledeep bp = ruledeep.getInstance("bp.model");
        String path = "http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + filepath + "?op=open&user.name=dsp";
        URL url = new URL(path);
        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream());
        BufferedReader br = new BufferedReader(inputStream);
        String value = br.readLine();
        while (null != (value= br.readLine())) {
            double[] label;
            String[] strs = value.split(",");
            int labell=Integer.parseInt(strs[strs.length-1]);
            label=new double[]{labell};
            String[] fea= Arrays.copyOfRange(strs,0,strs.length-1);
            double[] intTemp = new double[fea.length];
            for (int i = 0; i <fea.length; i++) {
                intTemp[i] = Double.parseDouble(fea[i]);
            }
            bp.fit(intTemp, label);
        }
        bp.savemodel("bp.model");
    }

    public double predict(String[] value) {
        Map<Integer, double[]> wfm= fm.W_fm;
        Map<String, String> X = new HashMap<String, String>();
        for (int i = 0; i < value.length; i++) {
            X.put(FNN.data_columns[i], value[i]);
        }
        int[] indices = fm.hash(X);
        double[] aa0 = wfm.get(0);
        for (int index : indices) {
            double[] vi;
            if (wfm.containsKey(index)) {
                vi = wfm.get(index);
            } else {
                vi = new double[fm.fm_dim];
            }
            aa0 = onArrayTogater(aa0, vi);
        }
        double[] y=new double[2];
        dnp.predict(aa0,y);
        return y[1];
    }

    public static void main(String[] args) throws Exception {

        FactorizationMachines FMM= FactorizationMachines.getInstance("fm.model");
        Map<Integer, double[]> wfm= FMM.W_fm;

        String[] value;
        CSVReader reader=new CSVReader(new FileReader(args[0]));
        File file=new File("op");
        Writer csvwriter=new FileWriter(file);
        CSVWriter cwriter=new CSVWriter(csvwriter);
        int nn=0;
        while((value=reader.readNext())!=null){
            nn+=1;
            double[] aa0 = null;
            String label=value[value.length-1];
            if (value.length!=28){
              System.out.println(value.length);}

            String[] fea= Arrays.copyOfRange(value,0,value.length-1);
            double[] intTemp = new double[fea.length*FMM.fm_dim];
            Map<String, String> X = new HashMap<String, String>();
            for (int i = 0; i < value.length - 1; i++) {
                X.put(data_columns[i], value[i]);
            }
            int[] indices = FMM.hash(X);
            for (int index : indices) {
                double[] vi;
                if (wfm.containsKey(index)) {
                    vi = wfm.get(index);
                } else {
                    vi = new double[FMM.fm_dim];
                }
                aa0 = onArrayTogater(aa0, vi);
            }
            int al=aa0.length;
            System.out.println(al);
            String[] nldata=new String[al+1];
            for (int i=0;i<aa0.length;i++){
                nldata[i]=Double.toString(aa0[i]);
            }
            nldata[aa0.length]=label;
            cwriter.writeNext(nldata);
        }
        csvwriter.close();
        System.out.println(nn);
        BpDeep bp1= BpDeep.getInstance();
        ArrayList<double[][]> fulldata= BpDeep.readdata("op");
        double[][] data = fulldata.get(0);
        double[][] target = fulldata.get(1);
        for(int n=0;n<60;n++) {
            for (int i = 0; i < data.length; i++) {
                bp1.train(data[i], target[i]);
            }
            System.out.print(n);
        }

        bp1.savemodel("nlmodel");
    }
}