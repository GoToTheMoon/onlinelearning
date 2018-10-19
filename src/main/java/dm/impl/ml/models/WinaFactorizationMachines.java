package dm.impl.ml.models;


import dm.utils.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.agrant.common.geo.GeoParser;



public class WinaFactorizationMachines  extends FactorizationMachines
{

    public static HashMap<String,List> imeikeyword=new HashMap<>();
    public static HashMap<String,List> androidkeyword=new HashMap<>();

    protected ArrayList<HashMap<String,List>> Init() throws IOException
    {
        String path = "http://l-httpfs.prod.qd1.corp.agrant.cn:14000/webhdfs/v1" + "/user/dm/winafeed/winaReqWordsAll.csv" + "?op=open&user.name=dsp";
        URL url = new URL(path);

        //ByteArrayEntity byteArrayEntity = new ByteArrayEntity(dmpRequestBuilder.build().toByteArray());
        InputStreamReader inputStream = new InputStreamReader(url.openStream());
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
        ArrayList<HashMap<String,List>> K=new ArrayList();
        K.add(0,imeikey);
        K.add(1,androidkey);
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


    private GeoParser parser = GeoParser.getInstance();
    private String[] columns;
    //private HashMap<String,Integer> allKeywords =new HashMap<String,Integer>();


    private WinaFactorizationMachines() throws IOException
    {
        //Init();
    }

    private WinaFactorizationMachines(double alpha,
                                        double beta,
                                        double L1,
                                        double L2,
                                        double alpha_V,
                                        double beta_V,
                                        double L1_V,
                                        double L2_V,
                                        int V_dim,
                                        double fm_initDev,
                                        double dropoutRate,
                                        int hashingSpace,
                                        String[] columns) throws IOException
    {
        super.init(alpha, beta, L1, L2,
                alpha_V, beta_V, L1_V, L2_V,
                V_dim, fm_initDev, dropoutRate, hashingSpace,
                columns);

    }


    private static final long serialVersionUID = "dm.ml.Models.WinaFactorizationMachines".hashCode();
    private static WinaFactorizationMachines instance;

    public static synchronized WinaFactorizationMachines getInstance(String filePath) throws Exception
    {
        if(instance==null)
            instance = loadModel(filePath);
        return instance;
    }

    public static synchronized WinaFactorizationMachines getInstance(double alpha,
                                                                       double beta,
                                                                       double L1,
                                                                       double L2,
                                                                       double alpha_fm,
                                                                       double beta_fm,
                                                                       double L1_fm,
                                                                       double L2_fm,
                                                                       int fm_dim,
                                                                       double fm_initDev,
                                                                       double dropoutRate,
                                                                       int hashingSpace,
                                                                       String[] columns) throws IOException
    {
        if(instance==null)
            instance = new WinaFactorizationMachines(alpha, beta, L1, L2,
                    alpha_fm,beta_fm,L1_fm,L2_fm,
                    fm_dim,fm_initDev,dropoutRate,
                    hashingSpace, columns);
        return instance;
    }


    public List<Object> predictAll(String JsonString)
    {
        JSONObject jsonObject = new JSONObject(JsonString);

        String imei=jsonObject.getString("imei");
        String androidId=jsonObject.getString("android_id");
        List historyKeywords=new ArrayList();

        String flag="0";
        if (imeikeyword.containsKey(imei)){
            historyKeywords= imeikeyword.get(imei);
            flag="1";
        }
        else if(androidkeyword.containsKey(androidId))
        {
            historyKeywords=androidkeyword.get(androidId);
            flag="1";
        }

        HashMap<String,String> X = new HashMap<>();

        for (int i=0;i<featuresCount-5;i++)
        {
            String key = columns[i];
            if (jsonObject.isNull(key))
            {
                X.put(key,"");
            }
            else
            {
                X.put(key,jsonObject.get(key).toString());
            }
        }

        String ip = X.get("ip");
        if(ip!=null)
        {
            X.put("region",parser.getCode(ip)[2]);
        }
        else
        {
            X.put("region","");
        }
        X.remove("ip");


        JSONArray adlist=jsonObject.getJSONArray("adList");
        List<Pair<String,Double>> pairs = new ArrayList<Pair<String, Double>>();

        for (int i=0;i<adlist.length();i++)
        {
            JSONObject adListJsonObject = new JSONObject(adlist.get(i).toString());

            String adid=adListJsonObject.getString("ad_id");
            //String title=jObject1.getString("ad_title");
            //String desc=jObject1.getString("ad_desc");
            String keywordList =adListJsonObject.getString("ad_keywords");
            String[] keywords = keywordList.split(" ");

            if (flag.equals("1"))
            {
                for (String keyword:keywords){
                    flag=historyKeywords.contains(keyword)?"2":"1";
                    if (flag.equals("2"))
                    {
                        break;
                    }
                }
            }

            //X.put("ad_title",title);
            //X.put("ad_desc",desc);

            double p = predict(X);

            pairs.add(new Pair<String,Double>(adid,p));
        }


        Collections.sort(pairs,new Comparator<Pair<String,Double>>() {
            @Override
            public int compare(Pair<String,Double> a, Pair<String,Double> b) {
                return b.getRight().compareTo(a.getRight());
            }
        });


        List<String> sortedAds = new ArrayList<>();
        for (Pair<String, Double> pair : pairs) {
            sortedAds.add(pair.getLeft());
        }

        List<String> flags=new ArrayList<String>();
        flags.add(flag);

        List result=new ArrayList<Object>();
        result.add(sortedAds);
        result.add(flags);
        return result;
    }



    public void reload(String filePath) throws Exception
    {
        WinaFactorizationMachines fm = loadModel(filePath);

        this.alpha = fm.alpha;
        this.beta = fm.beta;
        this.L1 = fm.L1;
        this.L2 = fm.L2;

        this.alpha_V = fm.alpha_V;
        this.beta_V = fm.beta_V;
        this.L1_V = fm.L1_V;
        this.L2_V = fm.L2_V;

        this.V_dim = fm.V_dim;
        this.fm_initDev = fm.fm_initDev;
        this.dropoutRate = fm.dropoutRate;

        this.hashingSpace = fm.hashingSpace;

        this.Z = fm.Z.clone();
        this.N = fm.N.clone();

        this.featuresCount = fm.featuresCount;
        this.columns = fm.columns.clone();

        this.W = new HashMap<Integer, Double>(this.featuresCount*2);

        //updateKeywords(fm.allKeywords);

        this.Z_V = new ConcurrentHashMap<>(fm.Z_V.size()*2);
        for (Map.Entry<Integer, double[]> entry : fm.Z_V.entrySet()) {
            Z_V.put(entry.getKey(),entry.getValue());
        }

        this.N_V = new ConcurrentHashMap<>(fm.N_V.size()*2);
        for (Map.Entry<Integer, double[]> entry : fm.N_V.entrySet()) {
            N_V.put(entry.getKey(),entry.getValue());
        }

        this.W_V = new ConcurrentHashMap<>(fm.W_V.size()*2);
        for (Map.Entry<Integer, double[]> entry : fm.W_V.entrySet()) {
            W_V.put(entry.getKey(),entry.getValue());
        }

    }


    public void saveModel(String fileName) throws Exception
    {
        super.saveModel(fileName);
    }


    protected static WinaFactorizationMachines loadModel(String fileName) throws Exception
    {
        WinaFactorizationMachines fm = new WinaFactorizationMachines();

        updateModelFromFile(fileName,fm);

        return fm;
    }

    public static void main(String[] args)
    {
        String JsonString = "{\n" +
                "\t\"reqid\": \" e09deb5dbfbc4ea38408c75e38597e44\",\n" +
                "\t\"logtime\": 1525772971232,\n" +
                "\t\"imei\": \" 864297035642179\",\n" +
                "\t\"android_id\": \" 2b8e97fd2d285dd0\",\n" +
                "\t\"mac\": \" 02:00:00:00:00:00\",\n" +
                "\t\"ua\": \" Mozilla/5.0 (Linux; Android 6.0.1; OPPO R9sk Build/MMB29M; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/55.0.2883.91 Mobile Safari/537.36\",\n" +
                "\t\"appid\": \"10\",\n" +
                "\t\"app_pkg\": \" com.pdragon.HD1010\",\n" +
                "\t\"ip\": \"117.152.32.205 \",\n" +
                "\t\"app_ver\":\"1\",\n" +
                "\t\"device_type\":\"0\",\n" +
                "\t\"ot\":\"1\",\n" +
                "\t\"ct\":\"0\",\n" +
                "\t\"callback_ip\": \" 123.178.83.245 \",\n" +
                "\t\"vendor\": \"OPPO\",\n" +
                "\t\"device\": \"Oppo\tR9sk\",\n" +
                "\t\"os\": \"Android\",\n" +
                "\t\"os_ver\": \"6.0\",\n" +
                "\t\"sh\": 1920,\n" +
                "\t\"sw\": 1080,\n" +
                "\t\"style\": \"banner\",\n" +
                "\t\"brw\": \" Chrome-Mobile-WebView\",\n" +
                "\t\"brw_ver\": \"55.0\",\n" +
                "\t\"adList\": [\n" +
                "\t{\n" +
                "\t\t\"ad_id\": \"0\",\n" +
                "\t\t\"ad_desc\": \"176传奇PK游戏\",\n" +
                "\t\t\"ad_title\": \"176传奇手游\",\n" +
                "\t\t\"ad_keywords\":\"游戏 传奇\"\n" +
                "\t},\n" +
                "\t{\n" +
                "\t\t\"ad_id\": \"1\",\n" +
                "\t\t\"ad_title\": \"在每天可以签到赚钱多的网站推荐\",\n" +
                "\t\t\"ad_desc\": \"签到赚钱\",\n" +
                "\t\t\"ad_keywords\":\"赚钱 签到 推荐 网站\"\n" +
                "\t},\n" +
                "\t{\n" +
                "\t\t\"ad_id\": \"2\",\n" +
                "\t\t\"ad_title\": \"凭信用卡借款5万,日息低至13元\",\n" +
                "\t\t\"ad_desc\": \"1分钟申请,快至30分钟放款\",\n" +
                "\t\t\"ad_keywords\":\"款 日息 信用卡\"\n" +
                "\t}]\n" +
                "}";

//        double alpha=0.01;
//        double beta=1.0;
//        double L1=0.1;
//        double L2=1.0;
//        double alpha_V=0.01;
//        double beta_V=1.0;
//        double L1_V=0.1;
//        double L2_V=1.0;
//        double fm_initDev=1.0;
//        double dropoutRate=0.001;
//        int factors = 4;
//        int hashingSpace = 1024;
//        String[] firstOrderColumns = {"reqid",
//                "appid",
//                "app_pkg",
//                "app_ver",
//                "code_id",
//                "ct",
//                "device_type",
//                "ip",
//                "os",
//                "os_ver",
//                "ot",
//                "sh",
//                "sw",
//                "vendor",
//                "device",
//                "brw"
//        };
//
//        WinaFactorizationMachines fm = WinaFactorizationMachines.getInstance(alpha, beta, L1,L2,
//                alpha_V,beta_V,L1_V,L2_V,factors,fm_initDev,dropoutRate,hashingSpace,firstOrderColumns);

        String path = "./";
        WinaFactorizationMachines fm = null;
        try
        {
            fm = WinaFactorizationMachines.loadModel(path);
            //fm.reload(path);
            List result = fm.predictAll(JsonString);

            for (Object r : result) {
                System.out.println(r);
            }
        }
        catch (Exception e)
        {
        }
    }

}


