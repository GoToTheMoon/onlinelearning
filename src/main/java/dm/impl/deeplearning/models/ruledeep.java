package dm.impl.deeplearning.models;

import Jama.Matrix;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import dm.utils.NormalDistribution;

import static java.lang.Math.max;

public class ruledeep {
    ArrayList<Matrix> layer;
    ArrayList<Matrix> layerm;
    ArrayList<Matrix>hid;
    double rate;

    private static ruledeep instance;

    public static synchronized ruledeep getInstance(String fileName) throws Exception
    {
        if(instance==null)
            instance = loadmodel(fileName);
        return instance;
    }

    public ruledeep(){}

    public ruledeep(int[] layernum,double rate){
        int len=layernum.length-1;
        this.rate=rate;
        layer=new ArrayList<Matrix>(len);

        layerm=new ArrayList<Matrix>(len);
        for (int i=0;i<layernum.length-1;i++){
            double[][] g=new double[layernum[i]][layernum[i+1]];
            for (int j=0;j<layernum[i];j++){
                for (int k=0;k<layernum[i+1];k++){
                    g[j][k]=NormalDistribution.getNumberInNormalDistribution(0,1);
                }
            }
            Matrix x=new Matrix(g);
            layer.add(x);
            layerm.add(x);
        }
    }

    public double[] predict(double[] data){
        hid=new ArrayList<Matrix>(layer.size());
        double[][] in=new double[1][];
        in[0]=data;
        Matrix inm= new Matrix(in);
        hid.add(inm);
        for (int j=0;j<layer.size()-1;j++) {
            inm = inm.times(layer.get(j));
            double[][] m=new double[1][];
            double[] h=new double[inm.getColumnDimension()];
            for (int i=0;i<h.length;i++){
                h[i]=relu(inm.get(0,i));
            }
            m[0]=h;
            inm=new Matrix(m);
            hid.add(inm);
        }
        double[] pre=inm.times(layer.get(layer.size()-1)).getArrayCopy()[0];
        for (int i=0;i<pre.length;i++){
            if (pre[i]<0){
                pre[i]=0;
            }
        }
        return pre;
    }

    public void fit(double[] data,double[] lable){
        double[] pre=predict(data);
        update(pre,lable);
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
            oos.writeObject(layerm);
            dos.writeDouble(rate);
        }finally {
            if(dos!=null)
                dos.close();
            if(fos!=null)
                fos.close();
        }
    }

    public static ruledeep loadmodel(String fileName) throws IOException, ClassNotFoundException {
        InputStream is = null;
        DataInputStream dis = null;
        ObjectInputStream oos;
        try {
            is = new FileInputStream(fileName);
            dis = new DataInputStream(is);
            oos = new ObjectInputStream(is);
            ArrayList<Matrix> layer1=(ArrayList<Matrix>)oos.readObject();
            ArrayList<Matrix> layerm1=(ArrayList<Matrix>)oos.readObject();
            double rate=dis.readDouble();
            ruledeep rbd=new ruledeep();
            rbd.rate=rate;
            rbd.layer=layer1;
            rbd.layerm=layerm1;
            return rbd;
        } finally {
            if (dis != null)
                dis.close();
            if (is != null)
                is.close();
        }
    }

    public void update(double[] pre,double[] targ){
        if (pre.length==targ.length){
            double[][] kdd=new double[1][];
            double[] ku=new double[pre.length];
            for (int i=0;i<pre.length;i++){
                ku[i]=2*(pre[i]-targ[i]);
            }
            kdd[0]=ku;
            Matrix kd=new Matrix(kdd);
            Matrix TR1=hid.get(hid.size()-1).transpose().times(kd);
            layerm.set(layer.size()-1,layer.get(layer.size()-1).minus(TR1.times(rate)));
            Matrix mdl=kd;
            for (int j=0;j<layer.size()-1;j++){
                mdl=mdl.times(layer.get(layer.size()-1-j).transpose());
                Matrix tra=mdl;

                Matrix TRR=tra;
                Matrix tr=hid.get(hid.size()-2-j).transpose().times(TRR);
                layerm.set(layer.size()-2-j,layer.get(layer.size()-2-j).minus(tr.times(rate)));
            }
            for (int k=0;k<layer.size();k++){
                layer.set(k, layerm.get(k));
            }
        }
    }

    public double getloss(double[] pre,double[] targ){
        double loss=0;
        for (int j=0;j<targ.length;j++){
            loss+=(pre[j]-targ[j])*(pre[j]-targ[j]);
        }
        return loss;
    }

    public double relu(double x){
        return max(0,x);
    }
    public static void main(String[] args){
        double[][] x=new double[1][3];
        Matrix m1 = Matrix.random(14, 10);
        System.out.println(Arrays.deepToString(x));
        System.out.println(m1.getColumnDimension());
    }
}
