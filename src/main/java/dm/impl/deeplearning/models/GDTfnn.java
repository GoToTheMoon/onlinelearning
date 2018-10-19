package dm.impl.deeplearning.models;

import com.agrant.common.geo.GeoParser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class GDTfnn {
    public static String[] data_columns =
            {"adid","adtype","width","height","channel","appid","codeid","reqtime","ip","ostype","dt","ot","osver","vendor","model","appver","creativetype","adw","adh","sw","sh","ct"};

    public static FactorizationMachines fm;
    public static Dropout dnp;
    private static GDTfnn instance;
    public static int ip_index= 8;

    public GDTfnn() throws IOException {
    }


    public static synchronized GDTfnn getInstance(String path) throws Exception
    {
        if(instance==null)
            instance = load(path);
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
            String[] valuess=value.split("\\|\\|");

            String[] values=get_feature(valuess);

            Map<String, String> X = new HashMap<String, String>();
            double y = Double.parseDouble(values[length]);
            for (int i = 0; i < values.length - 1; i++) {
                X.put(data_columns[i], values[i]);
            }
            fm.fit(X, y);
        }
        fm.saveModel("gdtfnn/fm.model");
    }

    public static void updatefm(String fileName) throws Exception {
        FactorizationMachines FMM= FactorizationMachines.getInstance("gdtfnn/fm.model");
        FMM.featuresCount=data_columns.length;
        String path = "http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + fileName + "?op=open&user.name=dsp";
        URL url = new URL(path);
        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream());
        BufferedReader br = new BufferedReader(inputStream);

        String value;
        int length = data_columns.length;

        while ((value = br.readLine()) != null) {
            String[] valuess=value.split("\\|\\|");

            String[] values=get_feature(valuess);
            Map<String, String> X = new HashMap<String, String>();
            double y = Double.parseDouble(values[values.length-1]);
            for (int i = 0; i < values.length - 1; i++) {
                X.put(data_columns[i], values[i]);
            }
            FMM.fit(X, y);
        }
        FMM.saveModel("gdtfnn/fm.model");
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


    public static String[] get_feature(String[] fea){
        String[] feat={fea[0],fea[2],fea[3],fea[4],fea[5],fea[14],fea[15],stampToHour(fea[18]),fea[19],fea[21],fea[22],fea[26],fea[27],fea[28],fea[29],fea[30],fea[32],fea[33],fea[34],fea[35],fea[36],fea[37],fea[39]};

        String ipp=feat[ip_index];
        GeoParser g=GeoParser.getInstance();
        String[] dip=g.getCode(ipp);
        String dipp=g.getProvName(dip[1]);

        feat[ip_index]=dipp;

        return feat;
    }

    public static String stampToHour(String s){
        String res;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long lt = new Long(s);
        Date date = new Date(lt);
        res = simpleDateFormat.format(date).split(" ")[1].split(":")[0];
        return res;
    }

    public void fit(String value) {
        int[] label;
        String[] strssd = value.split("\\|\\|");
        String[] strs=get_feature(strssd);
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
            X.put(GDTfnn.data_columns[i], fea[i]);
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

    public static GDTfnn load(String path) throws Exception {
        Dropout bp1 = Dropout.loadmodel(path+"/bp.model");
        FactorizationMachines FMM= FactorizationMachines.loadModel(path+"/fm.model");
        GDTfnn fnn;
        fnn = new GDTfnn();
        dnp=bp1;
        fm=FMM;
        return fnn;
    }

    public String predictAll(JSONObject jObject){

        int len= GDTfnn.data_columns.length;
        String[] a=new String[len];
        for (int i=1;i<len;i++){
            if (jObject.isNull(GDTfnn.data_columns[i])){
                a[i]="";
            }else{
                a[i]=jObject.get(GDTfnn.data_columns[i]).toString();
            }}
        JSONArray adlist=jObject.getJSONArray("adList");
        Map<Double,String> k = new HashMap<>();
        java.util.List h=new ArrayList<Double>();
        java.util.List o=new ArrayList<String>();
        double d=0.0000000000001;
        for (int i=0;i<adlist.length();i++){
            JSONObject jObject1 = new JSONObject(adlist.get(i).toString());

            String ad_id=jObject1.getString("ad_id");
            String adid=jObject1.getString("adid");
            a[0]=adid;
            double p=predict(get_feature(a));

            if (k.containsKey(p)){
                p=p-d;
                d=d+0.0000000000001;
                k.put(p,ad_id);
                h.add(p);
            }else {
                k.put(p,ad_id);
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

        return jrt.toString();
    }


    public void reload(String path) throws Exception {
        Dropout bp1 = Dropout.loadmodel(path+"/bp.model");
        FactorizationMachines FMM= FactorizationMachines.loadModel(path+"/fm.model");

        dnp=bp1;
        fm=FMM;
    }

    public void reload_id(HashMap<String,HashMap<String,List>> id){

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
            X.put(GDTfnn.data_columns[i], value[i]);
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
}