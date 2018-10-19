package dm.impl.ml.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GDT_model {
    private static GDT_model instance;
    private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    public static HashMap<String,String[]> adc_keys=new HashMap<>();
    HashMap<String,String[]> id=new HashMap<>();

    public static synchronized GDT_model getInstance(String file_path,HashMap<String,String[]> id) throws Exception {
        if(instance==null)
            instance = new GDT_model(id);
        return instance;
    }

    public GDT_model(HashMap<String,String[]> id) throws IOException {
        this.id=id;

        String path="http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/yukun.huang/gdt_adc.txt"+ "?op=open&user.name=dsp";

        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream(),"utf-8");
        BufferedReader br = new BufferedReader(inputStream);
        String value;

        while (null != (value= br.readLine())) {
            String[] values = value.split("：");

            String adc=values[0];
            String[] adkeys=values[1].split("、");

            adc_keys.put(adc,adkeys);
        }
        br.close();
    }

    public void reload_id(HashMap<String,String[]> id){
        this.id=id;
    }

    public void reload_model(String filePath) throws IOException {
        adc_keys=get_adc();
    }

    public HashMap<String,String[]> get_id() throws IOException {
        String path="http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/dm/huangyukun/gdt/ctr/imei_act.txt/part-00000"+ "?op=open&user.name=dsp";

        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream(),"utf-8");
        BufferedReader br = new BufferedReader(inputStream);
        String value= br.readLine();

        HashMap<String,String[]> id_ad=new HashMap<>();
        while (null != (value= br.readLine())) {
            String[] values = value.split(":");

            String adc=values[0];
            String[] adkeys=values[1].split(",");

            id_ad.put(adc,adkeys);
        }
        br.close();
        return id_ad;
    }

    private static HashMap<String,String[]> get_adc() throws IOException {

        String path="http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/yukun.huang/gdt_adc.txt"+ "?op=open&user.name=dsp";

        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream(),"utf-8");
        BufferedReader br = new BufferedReader(inputStream);
        String value;

        HashMap<String,String[]> adc_key=new HashMap<>();

        while (null != (value= br.readLine())) {
            String[] values = value.split("：");

            String ad=values[0];
            String[] adkeys=values[1].split("、");

            adc_key.put(ad,adkeys);
        }
        br.close();
        return adc_key;
    }

    public String predictAll(JSONObject jObject){
        String imei=jObject.getString("imei");

        List K=new ArrayList();

        JSONArray adlist=jObject.getJSONArray("adList");
        Map<Double,String> k = new HashMap<>();
        java.util.List h=new ArrayList<Double>();
        java.util.List o=new ArrayList<String>();

        double d=0.000001;

        if (rwl.isWriteLocked()|!id.containsKey(imei)){
            for (int i=0;i<adlist.length();i++){
                JSONObject jObject1 = new JSONObject(adlist.get(i).toString());

                String adid=jObject1.getString("ad_id");

                String ad_t=jObject1.getString("ad_title").replace(',',' ');
                String ad_d=jObject1.getString("ad_desc").replace(',',' ');

                o.add(i,adid);
            }
        }else{
            String[] imei_int=id.get(imei);
            for (int i=0;i<adlist.length();i++){
                JSONObject jObject1 = new JSONObject(adlist.get(i).toString());

                String adid=jObject1.getString("ad_id");

                String ad_t=jObject1.getString("ad_title").replace(',',' ');
                String ad_d=jObject1.getString("ad_desc").replace(',',' ');
                String ad=ad_t+ad_d;

                int ad_play=0;

                for (int j=1;j<adc_keys.size()+1;j++) {
                    String[] adk=adc_keys.get(String.valueOf(j));
                    for (String ad_k:adk) {
                        if (ad.contains(ad_k)) {
                            ad_play=j;
                            break;
                        }
                    }
                }

                String ad_s=String.format("%02d",ad_play);

                String ad_sc=ad_s+"1";
                String ad_sd=ad_s+"0";

                double p;
                if (Arrays.asList(imei_int).contains(ad_sc)) {
                    p=2;
                }else {
                    if(Arrays.asList(imei_int).contains(ad_sd)) {
                        p=-2;
                    }else {
                        p=0;
                    }
                }

                if (k.containsKey(p)){
                    p=p-d*Math.random();
                    d=d+0.000000001;
                    k.put(p,adid);
                    h.add(p);
                }else {
                    k.put(p,adid);
                    h.add(p);
                }
            }
            h.sort(Collections.reverseOrder());

            for (int i = 0; i<h.size(); i++){
                String u=k.get(h.get(i));
                o.add(i,u);
            }
        }

        JSONObject jrt=new JSONObject();
        jrt.put("adList",o);
        return jrt.toString();
    }
}