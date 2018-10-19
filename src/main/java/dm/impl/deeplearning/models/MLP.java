package dm.impl.deeplearning.models;

import dm.impl.deeplearning.base.HiddenLayer;
import dm.impl.deeplearning.base.LogisticRegression;

import java.io.*;
import java.util.Random;

public class MLP {

    public int n_in;
    public int n_hidden;
    public int n_hidden1;
    public int n_out;
    public String activation;
    public HiddenLayer hiddenLayer;
    public HiddenLayer hiddenLayer1;
    public LogisticRegression logisticLayer;
    public Random rng;
    public double rate;


    public MLP(int n_in, int n_hidden, int n_out,double rate,String activation, Random rng) {

        this.n_in = n_in;
        this.n_hidden = n_hidden;
        this.n_out = n_out;
        this.rate=rate;
        this.activation=activation;
        if (rng == null)rng = new Random(1234);
        this.rng = rng;

        // construct hiddenLayer
        this.hiddenLayer = new HiddenLayer(n_in, n_hidden, null, null, rng, activation);
        this.hiddenLayer1=new HiddenLayer(n_in, n_hidden, null, null, rng, activation);
        // construct logisticLayer
        this.logisticLayer = new LogisticRegression(n_hidden, n_out);
    }

    public MLP(int n_in, int n_hidden,int n_hidden1, int n_out,double rate,String activation, Random rng) {

        this.n_in = n_in;
        this.n_hidden = n_hidden;
        this.n_hidden1=n_hidden1;
        this.n_out = n_out;
        this.rate=rate;
        this.activation=activation;
        if (rng == null)rng = new Random(1234);
        this.rng = rng;

        // construct hiddenLayer
        this.hiddenLayer = new HiddenLayer(n_in, n_hidden, null, null, rng, activation);
        this.hiddenLayer1=new HiddenLayer(n_hidden,n_hidden1,null,null,new Random(234),activation);
        // construct logisticLayer
        if (n_hidden1==0) {
            this.logisticLayer = new LogisticRegression(n_hidden, n_out);
        }else{
            this.logisticLayer = new LogisticRegression(n_hidden1, n_out);
        }
    }


    public void train(double[][] train_X, int[][] train_Y) {
        double[] hidden_layer_input;
        double[] logistic_layer_input;
        double[] dy;

        for(int n=0; n<train_X.length; n++) {
            hidden_layer_input = new double[n_in];
            logistic_layer_input = new double[n_hidden];

            for(int j=0; j<n_in; j++) hidden_layer_input[j] = train_X[n][j];

            // forward hiddenLayer
            hiddenLayer.forward(hidden_layer_input, logistic_layer_input);

            // forward and backward logisticLayer
            // dy = new double[n_out];  // define delta of y for backpropagation
            dy = logisticLayer.train(logistic_layer_input, train_Y[n], rate); //, dy);

            // backward hiddenLayer
            hiddenLayer.backward(hidden_layer_input, null, logistic_layer_input, dy, logisticLayer.W,rate);
            }
    }

    public void fit(double[] data,int[] label){
        if (n_hidden1==0) {
            double[] logistic_layer_input = new double[n_hidden];
            hiddenLayer.forward(data, logistic_layer_input);
            double[] dy = logisticLayer.train(logistic_layer_input, label, rate);
            hiddenLayer.backward(data, null, logistic_layer_input, dy, logisticLayer.W, rate);
        }else{
            double[] dy1=new double[n_hidden1];
            double[] hid1_input=new double[n_hidden];
            double[] logistic_layer_input = new double[n_hidden1];
            hiddenLayer.forward(data, hid1_input);
            hiddenLayer1.forward(hid1_input,logistic_layer_input);
            double[] dy = logisticLayer.train(logistic_layer_input, label, rate);
            hiddenLayer1.backward(hid1_input, dy1, logistic_layer_input, dy, logisticLayer.W, rate);
            hiddenLayer.backward(data,null,hid1_input,dy1,hiddenLayer1.W,rate);
        }
    }

    public void savemodel(String filepath) throws Exception {
        FileOutputStream fos = null;
        DataOutputStream dos = null;
        ObjectOutputStream oos;
        try{
            fos = new FileOutputStream(filepath);
            dos = new DataOutputStream(fos);
            oos = new ObjectOutputStream(fos);
            dos.writeInt(n_in);
            dos.writeInt(n_hidden);
            dos.writeInt(n_hidden1);
            dos.writeInt(n_out);
            dos.writeDouble(rate);
            oos.writeUTF(activation);
            oos.writeObject(rng);
            oos.writeObject(hiddenLayer.W);
            oos.writeObject(hiddenLayer.b);
            oos.writeObject(hiddenLayer1.W);
            oos.writeObject(hiddenLayer1.b);
            oos.writeObject(logisticLayer.W);
            oos.writeObject(logisticLayer.b);
        } finally {
            if(dos!=null)
                dos.close();
            if(fos!=null)
                fos.close();
        }
    }

    public static MLP loadmodel(String fileName) throws Exception {
        InputStream is = null;
        DataInputStream dis = null;
        ObjectInputStream oos;
        try {
            is = new FileInputStream(fileName);
            dis = new DataInputStream(is);
            oos = new ObjectInputStream(is);
            int n_in=dis.readInt();
            int n_hidden=dis.readInt();
            int n_hidden1=dis.readInt();
            int n_out=dis.readInt();
            double rate=dis.readDouble();
            String activation=oos.readUTF();
            System.out.println(activation);
            Random rng=(Random)oos.readObject();

            double[][] h_w=(double[][])oos.readObject();
            double[] h_b=(double[])oos.readObject();
            double[][] h1_w=(double[][])oos.readObject();
            double[] h1_b=(double[])oos.readObject();
            double[][] l_w=(double[][])oos.readObject();
            double[] l_b=(double[])oos.readObject();
            MLP mlp=new MLP(n_in,n_hidden,n_hidden1,n_out,rate,activation,rng);
            mlp.hiddenLayer.W=h_w;
            mlp.hiddenLayer.b=h_b;
            mlp.hiddenLayer1.W=h1_w;
            mlp.hiddenLayer1.b=h1_b;
            mlp.logisticLayer.W=l_w;
            mlp.logisticLayer.b=l_b;
            return mlp;
        } finally {
            if (dis != null)
                dis.close();
            if (is != null)
                is.close();
        }
    }

    public void predict(double[] x, double[] y) {
        if (n_hidden1==0) {
            double[] logistic_layer_input = new double[n_hidden];
            hiddenLayer.forward(x, logistic_layer_input);
            logisticLayer.predict(logistic_layer_input, y);
        }else{
            double[] hidden_input = new double[n_hidden];
            double[] logistic_layer_input = new double[n_hidden1];
            hiddenLayer.forward(x, hidden_input);
            hiddenLayer1.forward(hidden_input,logistic_layer_input);
            logisticLayer.predict(logistic_layer_input,y);
        }
    }



    private static void test_mlp() {
        Random rng = new Random(123);

        double learning_rate = 0.1;
        int n_epochs = 5000;

        int train_N = 4;
        int test_N = 4;
        int n_in = 2;
        int n_hidden = 3;
        int n_out = 2;

        double[][] train_X = {
                {0., 0.},
                {0., 1.},
                {1., 0.},
        };

        int[][] train_Y = {
                {0, 1},
                {1, 0},
                {1, 0},
        };

        // construct MLP
        MLP classifier = new MLP(n_in, n_hidden,0, n_out,learning_rate,"sigmoid",rng);
        // train
        for(int epoch=0; epoch<n_epochs; epoch++) {
            for (int i=0;i<train_X.length;i++){
                classifier.fit(train_X[i],train_Y[i]);
            }
        }

        // test data
        double[][] test_X = {
                {0., 0.},
                {0., 1.},
                {1., 0.},
                {1., 1.},
        };

        double[][] test_Y = new double[test_N][n_out];


        // test
        for(int i=0; i<test_N; i++) {
            classifier.predict(test_X[i], test_Y[i]);
            for(int j=0; j<n_out; j++) {
                System.out.print(test_Y[i][j] + " ");
            }
            System.out.println();
        }

    }

    public static void main(String[] args) {
        test_mlp();
    }
}
