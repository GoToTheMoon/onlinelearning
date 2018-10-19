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

public class Winamodel_baidu_Monte extends WinaBaseModel implements WinaInterface {
    private static Winamodel_baidu_Monte instance;
    private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();


    public static HashMap<String,Double> ad_price_1=new HashMap<>();
    public static HashMap<String,Double> ad_compitition=new HashMap<>();

    public static synchronized Winamodel_baidu_Monte getInstance(String file_path,HashMap<String,HashMap<String,List>> id) throws IOException {
        if(instance==null)
            instance = new Winamodel_baidu_Monte(id);
        return instance;
    }

    public static synchronized Winamodel_baidu_Monte getInstance(String file_path, String mode) throws Exception {
        if(instance==null)
            instance = new Winamodel_baidu_Monte(mode);
        return instance;
    }


    public Winamodel_baidu_Monte(HashMap<String,HashMap<String,List>> id) throws IOException{
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
            Double compitition=Double.parseDouble(values[2]);

            ad_price_1.put(ad,price);
            ad_compitition.put(ad,compitition);
        }
        br.close();
    }


    public Winamodel_baidu_Monte(String mode) throws IOException {
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
            Double compitition=Double.parseDouble(values[2]);

            ad_price_1.put(ad,price);
            ad_compitition.put(ad,compitition);
        }
        br.close();
    }

    public void reload_id(HashMap<String,HashMap<String,List>> id){
        imeikeyword=id.get("imeikeyword");
        androidkeyword=id.get("androidkeyword");
    }

    public synchronized  void reload_model(String filePath) throws Exception {
        rwl.writeLock().lock();
        HashMap<String,HashMap<String,Double>> ad_pc=get_price_compitition();
        ad_price_1=ad_pc.get("ad_price_1");
        ad_compitition=ad_pc.get("ad_compitition");
        rwl.writeLock().unlock();
    }

    private static HashMap<String,HashMap<String,Double>> get_price_compitition() throws IOException {


        String path="http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/dm/context_ads/wina/sem/seedwords.csv"+ "?op=open&user.name=dsp";

        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream(),"utf-8");
        BufferedReader br = new BufferedReader(inputStream);
        String value= br.readLine();

        HashMap<String,Double> adp=new HashMap<>();
        HashMap<String,Double> adc=new HashMap<>();

        while (null != (value= br.readLine())) {
            String[] values = value.split(",");

            String ad=values[0];
            Double price=Double.parseDouble(values[1]);
            Double compitition=Double.parseDouble(values[2]);

            adp.put(ad,price);
            adc.put(ad,compitition);
        }

        HashMap<String,HashMap<String,Double>> K=new HashMap();

        K.put("ad_price_1",adp);
        K.put("ad_compitition",adc);
        return K;
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

        int con=-1;

        double pricesum=0;
        double[] prices=new double[adlist.length()+1];
        Map<Double,String> price_aid= new HashMap<>();
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

                double p;
                String[] htt=clickurl.split("[.]");

                if (i==0){
                    if (htt[0].contains("cpro")){
                        con=1;
                    }else{
                        con=-1;
                    }
                }

                if (htt[0].contains("cpro")){
                    double price=1;

                    double compit=1;

                    rwl.readLock().lock();
                    if (ad_price_1.containsKey(ad_t)){

                        price=ad_price_1.get(ad_t);

                        if (ad_compitition.containsKey(ad_t)){
                            Double c=ad_compitition.get(ad_t);
                            compit=c<20?0.0000000001:1;
                        }
                    }
                    rwl.readLock().unlock();

                    if (con==1){
                        pricesum+=price*compit;
                        prices[i]=pricesum;
                        price_aid.put(pricesum,adid);
                    }

                    if (con==1){
                        p=2*con*price*compit;
                    }else{
                        p=1/(2*con*price*compit);
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


            if (con==1) {
                prices[adlist.length()] = rd * pricesum;
                Arrays.sort(prices);
                int play_ad = Arrays.binarySearch(prices, rd * pricesum);
                String aid=price_aid.get(prices[play_ad+1]);
                k.remove(h.get(Integer.parseInt(aid)));
                h.set(Integer.parseInt(aid),10000000.0);
                k.put(10000000.0,aid);
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