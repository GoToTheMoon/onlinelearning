package dm.impl.deeplearning.models;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import com.agrant.common.geo.GeoParser;
import dm.impl.base.WinaBaseModel;
import dm.interfaces.WinaInterface;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.util.*;

public class WinaFNN extends WinaBaseModel implements WinaInterface {
    public static String[] data_columns = {
            "ad_title","ad_desc","appid","app_pkg",
            "app_ver","code_id","ct","device_type","ip",
           "os","os_ver","ot","sh",
            "sw","vendor","device",
            "brw"
    };
    public static BpDeep bp;
    public static FactorizationMachines fm;
    public static MLP mlp;
    public static Dropout dnp;
    private static WinaFNN instance;
    public static int ip_index=Arrays.binarySearch(data_columns,"ip");

    public WinaFNN() throws IOException {
    }


    public static synchronized WinaFNN getInstance(String path) throws Exception
    {
        if(instance==null)
            instance = load(path);
        return instance;
    }



    public List<HashMap> get_viewer_id() throws IOException {

        String path = "http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/dm/winafeed/winaReqWordsAll.csv" + "?op=open&user.name=dsp";
        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream());
        BufferedReader br = new BufferedReader(inputStream);
        String value= br.readLine();
        HashMap imeik=new HashMap();
        HashMap androidk=new HashMap();
        while (null != (value= br.readLine())) {
            String[] values=value.split(",");

            String[] keywords=onArrayTogaterstr(values[3].split(" "),values[4].split(" "));
            Set tSet = new HashSet(Arrays.asList(keywords));
            List keyword=new ArrayList(tSet);
            String imei=values[1];
            String android_id=values[2];
            imeik.put(imei,keyword);
            androidk.put(android_id,keyword);
        }
        List<HashMap> RT=new ArrayList();
        RT.add(0,imeik);
        RT.add(1,androidk);
        return RT;
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
        int hashingSpace = 1000000;

        FactorizationMachines fm = FactorizationMachines.getInstance(alpha, beta, L1, L2,
                alpha_fm, beta_fm, L1_fm, L2_fm, factors, fm_initDev, dropoutRate, hashingSpace, data_columns);
        String path = "http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + fileName + "?op=open&user.name=dsp";
        URL url = new URL(path);
        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream());
        BufferedReader br = new BufferedReader(inputStream);
        int length = data_columns.length;
        String value;
        while (null != (value= br.readLine())) {
            String[] valuess=value.split("<###>");

            String[] values=get_ip(valuess,3);

            Map<String, String> X = new HashMap<String, String>();
            double y = Double.parseDouble(values[length]);
            for (int i = 0; i < values.length - 1; i++) {
                X.put(data_columns[i], values[i]);
            }
            fm.fit(X, y);
        }
        fm.saveModel("winafnn/fm.model");
    }

    public static void updatefm(String fileName) throws Exception {
        FactorizationMachines FMM= FactorizationMachines.getInstance("winafnn/fm.model");
        FMM.featuresCount=data_columns.length;
        String path = "http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + fileName + "?op=open&user.name=dsp";
        URL url = new URL(path);
        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream());
        BufferedReader br = new BufferedReader(inputStream);

        String value;
        int length = data_columns.length;

        while ((value = br.readLine()) != null) {
            String[] valuess=value.split("<###>");

            String[] values=get_ip(valuess,3);
            Map<String, String> X = new HashMap<String, String>();
            double y = Double.parseDouble(values[values.length-1]);
            for (int i = 0; i < values.length - 1; i++) {
                X.put(data_columns[i], values[i]);
            }
            FMM.fit(X, y);
        }
        FMM.saveModel("winafnn/fm.model");
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


    public static String[] get_ip(String[] fea,int i){
        String ipp=fea[ip_index+i];
        GeoParser g=GeoParser.getInstance();
        String[] dip=g.getCode(ipp);
        String dipp=g.getProvName(dip[1]);

        fea[ip_index+i]=dipp;
        String[] dreq=Arrays.copyOfRange(fea,i,fea.length);
        return dreq;
    }


    public void fit(String value) {
        int[] label;
        String[] strssd = value.split("<###>");
        String[] strs=get_ip(strssd,3);
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
            X.put(WinaFNN.data_columns[i], fea[i]);
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

    public static WinaFNN load(String path) throws Exception {
        Dropout bp1 = Dropout.loadmodel(path+"/bp.model");
        FactorizationMachines FMM= FactorizationMachines.loadModel(path+"/fm.model");
        WinaFNN fnn=new WinaFNN();
        dnp=bp1;
        fm=FMM;
        return fnn;
    }

    public String predictAll(JSONObject jObject){

        String imei=jObject.getString("imei");
        String and_id=jObject.getString("android_id");
        List K=new ArrayList();
        String flag="0";

        if (imeikeyword.containsKey(imei)){
            K= imeikeyword.get(imei);
            flag="1";
        }else {if(androidkeyword.containsKey(and_id)){K=androidkeyword.get(and_id);flag="1";}}

        int len= WinaFNN.data_columns.length;
        String[] a=new String[len];
        for (int i=2;i<len;i++){
            if (jObject.isNull(WinaFNN.data_columns[i])){
                a[i]="";
            }else{
                a[i]=jObject.get(WinaFNN.data_columns[i]).toString();
        }}
        JSONArray adlist=jObject.getJSONArray("adList");
        Map<Double,String> k = new HashMap<>();
        java.util.List h=new ArrayList<Double>();
        java.util.List o=new ArrayList<String>();
        double d=0.0000000000001;
        for (int i=0;i<adlist.length();i++){
            JSONObject jObject1 = new JSONObject(adlist.get(i).toString());

            String adid=jObject1.getString("ad_id");
            String adtitle=jObject1.getString("ad_title");
            String addesc=jObject1.getString("ad_desc");
            String[] addkeyword=jObject1.getString("ad_keywords").split(" ");
            if (flag.equals("1")){
                for (String akw:addkeyword){
                    flag=K.contains(akw)?"2":"1";
                    if (flag.equals("2")){
                        break;
                    }
                }
            }
            a[0]=adtitle;
            a[1]=addesc;
            double p=predict(get_ip(a,0));

            if (k.containsKey(p)){
                p=p-d;
                d=d+0.0000000000001;
                k.put(p,adid);
                h.add(p);
            }else {
                k.put(p,adid);
                h.add(p);
            }
        }
        h.sort(Collections.reverseOrder());

        System.out.println(h);
        for (int i = 0; i<h.size(); i++){
            String u=k.get(h.get(i));
            o.add(i,u);
        }
        JSONObject jrt=new JSONObject();
        jrt.put("adList",o);
        jrt.put("sign",flag);
        return jrt.toString();
    }


    public void reload(String path) throws Exception {
        List<HashMap> viewer_id=get_viewer_id();
        Dropout bp1 = Dropout.loadmodel(path+"/bp.model");
        FactorizationMachines FMM= FactorizationMachines.loadModel(path+"/fm.model");
        imeikeyword=viewer_id.get(0);
        androidkeyword=viewer_id.get(1);
        dnp=bp1;
        fm=FMM;
    }

    public void reload_id(HashMap<String,HashMap<String,List>> id){
        imeikeyword=id.get("imeikeyword");
        androidkeyword=id.get("androidkeyword");
    }

    public void reload_model(String path) throws Exception {
        Dropout bp1 = Dropout.loadmodel(path+"/bp.model");
        FactorizationMachines FMM= FactorizationMachines.loadModel(path+"/fm.model");
        dnp=bp1;
        fm=FMM;
    }

    public double predict(String[] value) {

        Map<Integer, double[]> wfm= fm.W_fm;
        Map<String, String> X = new HashMap<>();
        for (int i = 0; i < value.length; i++) {
            X.put(WinaFNN.data_columns[i], value[i]);
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