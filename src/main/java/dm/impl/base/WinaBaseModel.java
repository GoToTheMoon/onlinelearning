package dm.impl.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public abstract class WinaBaseModel
{
    public static HashMap<String,List> imeikeyword=new HashMap<>();
    public static HashMap<String,List> androidkeyword=new HashMap<>();
    public static HashMap<String,Double> ad_price=new HashMap<>();


    public WinaBaseModel(String mode) throws IOException {
        HashMap<String,HashMap<String,List>> wm=Init(mode);
        imeikeyword=wm.get("imeikeyword");
        androidkeyword=wm.get("androidkeyword");
    }


    public WinaBaseModel() {

    }

    public void reload(String filePath,HashMap<String,HashMap<String,List>> id) throws Exception{
        imeikeyword=id.get("imeikeyword");
        androidkeyword=id.get("androidkeyword");
    }


    public static HashMap<String,Double> get_price() throws IOException {

        HashMap<String,Double> ad_price_get=new HashMap<>();

        String path="http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/dm/zhouyonglong/context_ads/wina/sem/keywords.csv"+ "?op=open&user.name=dsp";

        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream(),"utf-8");
        BufferedReader br = new BufferedReader(inputStream);
        String value= br.readLine();

        while (null != (value= br.readLine())) {
            String[] values = value.split(",");

            String ad=values[0];
            Double price=Double.parseDouble(values[1]);

            ad_price_get.put(ad,price);
        }
        return ad_price_get;
    }


    public static HashMap<String,HashMap<String,List>> Init(String mode) throws IOException {
        String path;
        if (mode.equals("test")){
            path="http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/yukun.huang/winaReqWordsAll_test.csv"+ "?op=open&user.name=dsp";
        } else{
            path="http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/dm/winafeed/winaReqWordsAll.csv"+ "?op=open&user.name=dsp";
        }

        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream(),"utf-8");
        BufferedReader br = new BufferedReader(inputStream);
        String value= br.readLine();
        HashMap<String,List> imeikey=new HashMap<>();
        HashMap<String,List> androidkey=new HashMap<>();
        while (null != (value= br.readLine())) {
            String[] values=value.split(",");

            String[] keywords=onArrayTogaterstr(values[3].split(" "),values[4].split(" "));
            Set tSet = new HashSet(Arrays.asList(keywords));
            List keyword=new ArrayList(tSet);

            String imei=values[1];
            String android_id=values[2];

            imeikey.put(imei,keyword);
            androidkey.put(android_id,keyword);
        }
        br.close();
        HashMap<String,HashMap<String,List>> K=new HashMap<>();
        K.put("imeikeyword",imeikey);
        K.put("androidkeyword",androidkey);
        return K;
    }

    public String get_yesterday_date(){
        SimpleDateFormat dateFormat= new SimpleDateFormat("yyyyMMdd");
        Date date=new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        date = calendar.getTime();
        return dateFormat.format(date);
    }

    public static String[] onArrayTogaterstr(String[] aa, String[] bb) {

        if (aa == null) {
            return bb;
        }
        String[] collectionStr = new String[aa.length + bb.length];
        System.arraycopy(aa, 0, collectionStr, 0, aa.length);
        System.arraycopy(bb, 0, collectionStr, aa.length, aa.length + bb.length - aa.length);
        return collectionStr;
    }
}
