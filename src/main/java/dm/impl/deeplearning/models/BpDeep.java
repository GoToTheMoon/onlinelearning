package dm.impl.deeplearning.models;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import static java.lang.Math.max;

public class BpDeep {
    public double[][] layer;//神经网络各层节点
    public double[][] layerErr;//神经网络各节点误差
    public double[][][] layer_weight;//各层节点权重
    public double[][][] layer_weight_delta;//各层节点权重动量
    public double mobp;//动量系数
    public double rate;//学习系数
    private static BpDeep instance;

    public BpDeep(){}
    public BpDeep(int[] layernum, double rate, double mobp){
        this.mobp = mobp;
        this.rate = rate;
        layer = new double[layernum.length][];
        layerErr = new double[layernum.length][];
        layer_weight = new double[layernum.length][][];
        layer_weight_delta = new double[layernum.length][][];
        Random random = new Random();
        for(int l=0;l<layernum.length;l++){
            layer[l]=new double[layernum[l]];
            layerErr[l]=new double[layernum[l]];
            if(l+1<layernum.length){
                layer_weight[l]=new double[layernum[l]+1][layernum[l+1]];
                layer_weight_delta[l]=new double[layernum[l]+1][layernum[l+1]];
                for(int j=0;j<layernum[l]+1;j++)
                    for(int i=0;i<layernum[l+1];i++)
                        layer_weight[l][j][i]=random.nextDouble();//随机初始化权重
            }
        }
    }
    public static synchronized BpDeep getInstance(String fileName) throws Exception
    {
        if(instance==null)
            instance = loadmodel(fileName);
        return instance;
    }

    public static synchronized BpDeep getInstance()
    {
        if(instance==null)
            instance = new BpDeep(new int[]{108,20,2}, 0.05, 0.05);
        return instance;
    }
    //逐层向前计算输出
    public double[] predict(double[] in){
        if (in.length!=layer[0].length){
            int layerlength=layer[0].length;
            int inlength=in.length;
            System.out.printf("the layer need double[] length %d,actual length %d",layerlength,inlength);
        }
        for(int l=1;l<layer.length;l++){
            for(int j=0;j<layer[l].length;j++){
                double z=layer_weight[l-1][layer[l-1].length][j];
                for(int i=0;i<layer[l-1].length;i++){
                    layer[l-1][i]=l==1?in[i]:layer[l-1][i];
                    z+=layer_weight[l-1][i][j]*layer[l-1][i];
                }
                layer[l][j]=Sigmoid(z);
            }
        }
        return layer[layer.length-1];
    }

    //Activation Functions
    public double Sigmoid(double x){
       return 1/(1+Math.exp(-x));
    }

    public double tanh(double x){
       return  (2*Sigmoid(2*x))-1;
    }

    public double relu(double x){
        return max(0,x);
    }
    //逐层反向计算误差并修改权重
    public void updateWeight(double[] tar){
        int l=layer.length-1;
        for(int j=0;j<layerErr[l].length;j++)
            layerErr[l][j]=layer[l][j]*(1-layer[l][j])*(tar[j]-layer[l][j]);

        while(l-->0){
            for(int j=0;j<layerErr[l].length;j++){
                double z = 0.0;
                for(int i=0;i<layerErr[l+1].length;i++){
                    z=z+l>0?layerErr[l+1][i]*layer_weight[l][j][i]:0;
                    layer_weight_delta[l][j][i]= mobp*layer_weight_delta[l][j][i]+rate*layerErr[l+1][i]*layer[l][j];//隐含层动量调整
                    layer_weight[l][j][i]+=layer_weight_delta[l][j][i];//隐含层权重调整
                    if(j==layerErr[l].length-1){
                        layer_weight_delta[l][j+1][i]= mobp*layer_weight_delta[l][j+1][i]+rate*layerErr[l+1][i];//截距动量调整
                        layer_weight[l][j+1][i]+=layer_weight_delta[l][j+1][i];//截距权重调整
                    }
                }
                layerErr[l][j]=z*layer[l][j]*(1-layer[l][j]);//记录误差
            }
        }
    }

    public void train(double[] in, double[] tar){
        double[] out = predict(in);
        updateWeight(tar);
    }

    public static ArrayList<double[][]> readdata(String filepath) throws IOException {
        CSVReader reader=new CSVReader(new FileReader(filepath));
        String[] value;
        double[][] trdata;
        double[][] ld;
        ArrayList<double[]> td = new ArrayList<double[]>();
        ArrayList<double[]> labell=new ArrayList<double[]>();
        ArrayList<double[][]> trd=new ArrayList<double[][]>();
        while((value=reader.readNext())!=null){

            int label=Integer.parseInt(value[value.length-1]);
            if (label==1){
                labell.add(new double[]{0,1});
            }else{
                labell.add(new double[]{1,0});
            }
            String[] fea= Arrays.copyOfRange(value,0,value.length-1);
            double[] intTemp = new double[fea.length];
            for (int i = 0; i <fea.length; i++) {
                intTemp[i] = Double.parseDouble(fea[i]);
            }
            td.add(intTemp);
        }
        trdata=td.toArray(new double[0][0]);
        ld=labell.toArray(new double[0][0]);
        trd.add(trdata);
        trd.add(ld);
        return trd;
    }

    public void savemodel(String fileName)throws Exception{
        FileOutputStream fos = null;
        DataOutputStream dos = null;
        ObjectOutputStream oos;
        try{
            fos = new FileOutputStream(fileName);
            dos = new DataOutputStream(fos);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(layer);
            oos.writeObject(layer_weight);
            oos.writeObject(layerErr);
            oos.writeObject(layer_weight_delta);
            dos.writeDouble(rate);
            dos.writeDouble(mobp);
        } finally {
            if(dos!=null)
                dos.close();
            if(fos!=null)
                fos.close();
        }
    }

    public static BpDeep loadmodel(String fileName) throws Exception {
        InputStream is = null;
        DataInputStream dis = null;
        ObjectInputStream oos;
        try {
            is = new FileInputStream(fileName);
            dis = new DataInputStream(is);
            oos = new ObjectInputStream(is);
            double[][] layer=(double[][])oos.readObject();
            double[][][] layerwight=(double[][][])oos.readObject();
            double[][] layererr=(double[][])oos.readObject();
            double[][][] layer_weight_delta=(double[][][])oos.readObject();
            double rate=dis.readDouble();
            double mobp=dis.readDouble();
            BpDeep bd=new BpDeep();
            bd.rate=rate;
            bd.mobp=mobp;
            bd.layer=layer;
            bd.layer_weight=layerwight;
            bd.layerErr=layererr;
            bd.layer_weight_delta=layer_weight_delta;
            return bd;
        } finally {
            if (dis != null)
                dis.close();
            if (is != null)
                is.close();
        }
    }

    public BpDeep fit(double[][] data,double[][] label) {
        for(int n=0;n<60;n++) {
            for (int i = 0; i < data.length; i++) {
                train(data[i], label[i]);
            }
        }
        BpDeep bp=new BpDeep();
        bp.layer=this.layer;
        bp.layer_weight=this.layer_weight;
        bp.layer_weight_delta=this.layer_weight_delta;
        bp.layerErr=this.layerErr;
        bp.mobp=this.mobp;
        bp.rate=this.rate;
        return bp;
    }
    public static void main(String[] args) throws Exception {
        //初始化神经网络的基本配置
        //第一个参数是一个整型数组，表示神经网络的层数和每层节点数，比如{3,10,10,10,10,2}表示输入层是3个节点，输出层是2个节点，中间有4层隐含层，每层10个节点
        //第二个参数是学习步长，第三个参数是动量系数

        BpDeep bp = getInstance();

        ArrayList<double[][]> fulldata=readdata(args[0]);
        //设置样本数据，对应上面的4个二维坐标数据
        double[][] data = fulldata.get(0);
        //设置目标数据，对应4个坐标数据的分类
        double[][] target = fulldata.get(1);

        //迭代训练n次
        for(int n=0;n<60;n++) {
            for (int i = 0; i < data.length; i++) {
                bp.train(data[i], target[i]);
            }
            System.out.print(n);
        }

        bp.savemodel("nlmodel");

        BpDeep instance= getInstance("nlmodel");

        //根据训练结果来检验样本数据
        CSVReader reader=new CSVReader(new FileReader(args[1]));
        String[] value;
        File file=new File("op");
        Writer csvwriter=new FileWriter(file);
        CSVWriter cwriter=new CSVWriter(csvwriter);
        Date day = new Date();
        int num=0;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println(df.format(day));
        while((value=reader.readNext())!=null){
            num+=1;
            String label=value[0];
            String[] fea= Arrays.copyOfRange(value,1,value.length);
            double[] intTemp = new double[fea.length];
            for (int i = 0; i <fea.length; i++) {
                intTemp[i] = Double.parseDouble(fea[i]);
            }
            Double result = instance.predict(intTemp)[1];
            String[] RET={label,Double.toString(result)};
            cwriter.writeNext(RET);
        }
        csvwriter.close();
        Date day1 = new Date();
        System.out.println(df.format(day1));
        System.out.println(num);
    }
}