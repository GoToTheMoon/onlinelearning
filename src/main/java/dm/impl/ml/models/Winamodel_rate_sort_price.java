package dm.impl.ml.models;

import dm.impl.base.WinaBaseModel;
import dm.interfaces.WinaInterface;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Winamodel_rate_sort_price extends WinaBaseModel implements WinaInterface {
    private static Winamodel_rate_sort_price instance;
    private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    public static synchronized Winamodel_rate_sort_price getInstance(String file_path,HashMap<String,HashMap<String,List>> id) throws IOException {
        if(instance==null)
            instance = new Winamodel_rate_sort_price(id);
        return instance;
    }

    public static synchronized Winamodel_rate_sort_price getInstance(String file_path, String mode) throws Exception {
        if(instance==null)
            instance = new Winamodel_rate_sort_price(mode);
        return instance;
    }


    public Winamodel_rate_sort_price(HashMap<String,HashMap<String,List>> id) throws IOException{
        imeikeyword=id.get("imeikeyword");
        androidkeyword=id.get("androidkeyword");

        String path="http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/dm/context_ads/wina/sem/seedwords.csv"+ "?op=open&user.name=dsp";

        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream(),"utf-8");
        BufferedReader br = new BufferedReader(inputStream);
        String value= br.readLine();

        while (null != (value= br.readLine())) {
            String[] values = value.split(",");

            String ad=values[0];
            Double price=Double.parseDouble(values[1]);

            ad_price.put(ad,price);
        }
        br.close();
    };


    public Winamodel_rate_sort_price(String mode) throws IOException {
        super(mode);

        String path="http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/dm/context_ads/wina/sem/seedwords.csv"+ "?op=open&user.name=dsp";

        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream(),"utf-8");
        BufferedReader br = new BufferedReader(inputStream);
        String value= br.readLine();

        while (null != (value= br.readLine())) {
            String[] values = value.split(",");

            String ad=values[0];
            Double price=Double.parseDouble(values[1]);

            ad_price.put(ad,price);
        }
        br.close();
    }

    public void reload_id(HashMap<String,HashMap<String,List>> id){
        imeikeyword=id.get("imeikeyword");
        androidkeyword=id.get("androidkeyword");
    }

    public void reload_model(String filePath) throws Exception {
        rwl.writeLock().lock();
        ad_price=get_price();
        rwl.writeLock().unlock();
    }

    public String predictAll(JSONObject jObject){

        String os=jObject.getString("os");
        String flag="0";
        List K=new ArrayList();
        if (os.equalsIgnoreCase("IOS")){
            String idfa=jObject.getString("idfa");
        }else {
            String imei=jObject.getString("imei");
            String and_id=jObject.getString("android_id");

            if (imeikeyword.containsKey(imei)){
                K= imeikeyword.get(imei);
                flag="1";
            }else {if(androidkeyword.containsKey(and_id)){K=androidkeyword.get(and_id);flag="1";}}

        }

        JSONArray adlist=jObject.getJSONArray("adList");
        Map<Double,String> k = new HashMap<>();
        java.util.List h=new ArrayList<Double>();
        java.util.List o=new ArrayList<String>();

        Random random = new Random();
        double rd=random.nextFloat();

        int con;

        if (rd>0.6){
            con=-1;
        }else {
            con=1;
        }
        double d=0.000001;

        if (rwl.isWriteLocked()){
            for (int i=0;i<adlist.length();i++){
                JSONObject jObject1 = new JSONObject(adlist.get(i).toString());

                String adid=jObject1.getString("ad_id");

                String clickurl=jObject1.getString("click_url");
                String ad_t=jObject1.getString("ad_title").replace(',',' ');

                String[] addkeyword=jObject1.getString("ad_keywords").split(" ");
                if (flag.equals("1")){
                    for (String akw:addkeyword){
                        flag=K.contains(akw)?"2":"1";
                        if (flag.equals("2")){
                            break;
                        }
                    }
                }

                o.add(i,adid);
            }
        }else{
            for (int i=0;i<adlist.length();i++){
                JSONObject jObject1 = new JSONObject(adlist.get(i).toString());

                String adid=jObject1.getString("ad_id");

                String clickurl=jObject1.getString("click_url");
                String ad_t=jObject1.getString("ad_title");

                String[] addkeyword=jObject1.getString("ad_keywords").split(" ");
                if (flag.equals("1")){
                    for (String akw:addkeyword){
                        flag=K.contains(akw)?"2":"1";
                        if (flag.equals("2")){
                            break;
                        }
                    }
                }

                double p;
                String[] htt=clickurl.split("[.]");
                if (htt[0].contains("cpro")){
                    double price=1;

                    rwl.readLock().lock();
                    if (ad_price.containsKey(ad_t)){

                        price=ad_price.get(ad_t);
                    }
                    rwl.readLock().unlock();
                    if (con==1){
                        p=2*con*price;
                    }else{
                        p=1/(2*con*price);
                    }
                }else{
                    p=-2*con;
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
        jrt.put("sign",flag);
        return jrt.toString();
    }
}