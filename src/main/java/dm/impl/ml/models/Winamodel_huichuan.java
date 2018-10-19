package dm.impl.ml.models;

import dm.impl.base.WinaBaseModel;
import dm.interfaces.WinaInterface;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class Winamodel_huichuan extends WinaBaseModel implements WinaInterface {
    private static Winamodel_huichuan instance;
    public static synchronized Winamodel_huichuan getInstance(String file_path,HashMap<String,HashMap<String,List>> id) throws Exception {

        if(instance==null)
            instance = new Winamodel_huichuan(id);
        return instance;
    }

    public static synchronized Winamodel_huichuan getInstance(String file_path,String mode) throws Exception {
        if(instance==null)
            instance = new Winamodel_huichuan(mode);
        return instance;
    }

    public Winamodel_huichuan(HashMap<String,HashMap<String,List>> id) throws IOException {

        imeikeyword=id.get("imeikeyword");
        androidkeyword=id.get("androidkeyword");
    }

    public Winamodel_huichuan(String mode) throws IOException {
        super(mode);
    }

    public void reload_id(HashMap<String,HashMap<String,List>> id){
        imeikeyword=id.get("imeikeyword");
        androidkeyword=id.get("androidkeyword");
    }

    public void reload_model(String filePath) throws Exception {

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

        double d=0.000001;
        for (int i=0;i<adlist.length();i++){
            JSONObject jObject1 = new JSONObject(adlist.get(i).toString());

            String adid=jObject1.getString("ad_id");

            String clickurl=jObject1.getString("click_url");
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
                p=2;
            }else{
                p=0;
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
        JSONObject jrt=new JSONObject();
        jrt.put("adList",o);
        jrt.put("sign",flag);
        return jrt.toString();
    }
}