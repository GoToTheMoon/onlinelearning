package dm.impl.deeplearning.models;

import dm.impl.deeplearning.base.HiddenLayer;
import dm.impl.deeplearning.base.LogisticRegression;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Dropout {
    public int N;
    public int n_in;
    public int[] hidden_layer_sizes;
    public int n_out;
    public int n_layers;
    public HiddenLayer[] hiddenLayers;
    public LogisticRegression logisticLayer;
    public Random rng;
    public double learning_rate;
    boolean dropout;
    double p_dropout;
    String activation;

    public Dropout(int n_in, int[] hidden_layer_sizes, int n_out, Random rng, String activation,double learning_rate,boolean dropout,double p_dropout) {
        this.learning_rate=learning_rate;
        this.n_in = n_in;
        this.hidden_layer_sizes = hidden_layer_sizes;
        this.n_layers = hidden_layer_sizes.length;
        this.n_out = n_out;
        this.dropout=dropout;
        this.p_dropout=p_dropout;
        this.activation=activation;

        this.hiddenLayers = new HiddenLayer[n_layers];

        if (rng == null) rng = new Random(1234);
        this.rng = rng;

        if (activation == null) activation = "ReLU";

        // construct multi-layer
        int input_size;
        for(int i=0; i<this.n_layers; i++) {
            // layer_size
            if(i == 0) {
                input_size = n_in;
            } else {
                input_size = hidden_layer_sizes[i-1];
            }

            // construct hiddenLayer
            this.hiddenLayers[i] = new HiddenLayer(input_size, hidden_layer_sizes[i], null, null, rng, activation);

        }

        // construct logisticLayer
        this.logisticLayer = new LogisticRegression(hidden_layer_sizes[this.n_layers-1], n_out);

    }

    public void train(int epochs, double[][] train_X, int[][] train_Y, boolean dropout, double p_dropout, double lr) {
        List<int[]> dropout_masks;
        List<double[]> layer_inputs;
        double[] layer_input;
        double[] layer_output = new double[0];

        for(int epoch=0; epoch<epochs; epoch++) {

            for(int n=0; n<N; n++) {

                dropout_masks = new ArrayList<>(n_layers);
                layer_inputs = new ArrayList<>(n_layers+1);  // +1 for logistic layer

                // forward hiddenLayers
                for(int i=0; i<n_layers; i++) {

                    if(i == 0) layer_input = train_X[n];
                    else layer_input = layer_output.clone();

                    layer_inputs.add(layer_input.clone());

                    layer_output = new double[hidden_layer_sizes[i]];
                    hiddenLayers[i].forward(layer_input, layer_output);

                    if(dropout) {
                        int[] mask;
                        mask = hiddenLayers[i].dropout(layer_output.length, p_dropout, rng);
                        for(int j=0; j<layer_output.length; j++) layer_output[j] *= mask[j];

                        dropout_masks.add(mask.clone());
                    }

                }


                // forward & backward logisticLayer
                double[] logistic_layer_dy; // = new double[n_out];
                logistic_layer_dy = logisticLayer.train(layer_output, train_Y[n], lr); //, logistic_layer_dy);
                layer_inputs.add(layer_output.clone());

                // backward hiddenLayers
                double[] prev_dy = logistic_layer_dy;
                double[][] prev_W;
                double[] dy = new double[0];

                for(int i=n_layers-1; i>=0; i--) {

                    if(i == n_layers-1) {
                        prev_W = logisticLayer.W;
                    } else {
                        prev_dy = dy.clone();
                        prev_W = hiddenLayers[i+1].W;
                    }

                    if(dropout) {
                        for(int j=0; j<prev_dy.length; j++) {
                            prev_dy[j] *= dropout_masks.get(i)[j];
                        }
                    }

                    dy = new double[hidden_layer_sizes[i]];
                    hiddenLayers[i].backward(layer_inputs.get(i), dy, layer_inputs.get(i+1), prev_dy, prev_W, lr);
                }

            }
        }
    }

    public void fit(double[] train_X, int[] train_Y) {
        List<int[]> dropout_masks;
        List<double[]> layer_inputs;
        double[] layer_input;
        double[] layer_output = new double[0];

        dropout_masks = new ArrayList<>(n_layers);
        layer_inputs = new ArrayList<>(n_layers+1);  // +1 for logistic layer

        // forward hiddenLayers
        for(int i=0; i<n_layers; i++) {

            if(i == 0) layer_input = train_X;
            else layer_input = layer_output.clone();

            layer_inputs.add(layer_input.clone());

            layer_output = new double[hidden_layer_sizes[i]];
            hiddenLayers[i].forward(layer_input, layer_output);

            if(dropout) {
                int[] mask;
                mask = hiddenLayers[i].dropout(layer_output.length, p_dropout, rng);
                for(int j=0; j<layer_output.length; j++) layer_output[j] *= mask[j];

                dropout_masks.add(mask.clone());
            }

        }


        // forward & backward logisticLayer
        double[] logistic_layer_dy; // = new double[n_out];
        logistic_layer_dy = logisticLayer.train(layer_output, train_Y, learning_rate); //, logistic_layer_dy);
        layer_inputs.add(layer_output.clone());

        // backward hiddenLayers
        double[] prev_dy = logistic_layer_dy;
        double[][] prev_W;
        double[] dy = new double[0];

        for(int i=n_layers-1; i>=0; i--) {

            if(i == n_layers-1) {
                prev_W = logisticLayer.W;
            } else {
                prev_dy = dy.clone();
                prev_W = hiddenLayers[i+1].W;
            }

            if(dropout) {
                for(int j=0; j<prev_dy.length; j++) {
                    prev_dy[j] *= dropout_masks.get(i)[j];
                }
            }

            dy = new double[hidden_layer_sizes[i]];
            hiddenLayers[i].backward(layer_inputs.get(i), dy, layer_inputs.get(i+1), prev_dy, prev_W, learning_rate);
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
            oos.writeObject(hidden_layer_sizes);

            dos.writeInt(n_out);
            dos.writeDouble(learning_rate);
            oos.writeUTF(activation);
            oos.writeObject(rng);

            dos.writeBoolean(dropout);
            dos.writeDouble(p_dropout);

            for (int i=0;i<n_layers;i++){
                oos.writeObject(hiddenLayers[i].W);
                oos.writeObject(hiddenLayers[i].b);
            }

            oos.writeObject(logisticLayer.W);
            oos.writeObject(logisticLayer.b);
        } finally {
            if(dos!=null)
                dos.close();
            if(fos!=null)
                fos.close();
        }
    }

    public static Dropout loadmodel(String fileName) throws Exception {
        InputStream is = null;
        DataInputStream dis = null;
        ObjectInputStream oos;
        try {
            is = new FileInputStream(fileName);
            dis = new DataInputStream(is);
            oos = new ObjectInputStream(is);
            int n_in=dis.readInt();
            int[] hidden_layer_sizes=(int[])oos.readObject();
            int layers=hidden_layer_sizes.length;

            int n_out=dis.readInt();
            double rate=dis.readDouble();
            String activation=oos.readUTF();
            System.out.println(activation);
            Random rng=(Random)oos.readObject();
            Boolean dropout=dis.readBoolean();
            double p_dropout=dis.readDouble();

            Dropout dlp=new Dropout(n_in,hidden_layer_sizes,n_out,rng,activation,rate,dropout,p_dropout);

            for (int i=0;i<layers;i++){
                double[][] h_w=(double[][])oos.readObject();
                double[] h_b=(double[])oos.readObject();
                dlp.hiddenLayers[i].W=h_w;
                dlp.hiddenLayers[i].b=h_b;
            }

            double[][] l_w=(double[][])oos.readObject();
            double[] l_b=(double[])oos.readObject();

            dlp.logisticLayer.W=l_w;
            dlp.logisticLayer.b=l_b;
            return dlp;
        } finally {
            if (dis != null)
                dis.close();
            if (is != null)
                is.close();
        }
    }

    public void pretest(double p_dropout) {
        for(int i=0; i<n_layers; i++) {
            int in;
            int out;

            if (i == 0) in = n_in;
            else in = hidden_layer_sizes[i];

            if (i == n_layers - 1) out = n_out;
            else out = hidden_layer_sizes[i+1];


            for (int l = 0; l < out; l++) {
                for (int m = 0; m < in; m++) {
                    hiddenLayers[i].W[l][m] *= 1 - p_dropout;
                }
            }
        }
    }


    public void predict(double[] x, double[] y) {
        double[] layer_input;
        double[] layer_output = new double[0];

        for(int i=0; i<n_layers; i++) {

            if(i == 0) layer_input = x;
            else layer_input = layer_output.clone();

            layer_output = new double[hidden_layer_sizes[i]];

            hiddenLayers[i].forward(layer_input, layer_output);
        }

        logisticLayer.predict(layer_output, y);
    }


    private static void test_dropout() {
        Random rng = new Random(123);

        double learning_rate = 0.1;
        int n_epochs = 5000;

        int train_N = 4;
        int test_N = 4;
        int n_in = 2;
        int[] hidden_layer_sizes = {10, 4};
        int n_out = 2;

        boolean dropout = true;
        double p_dropout = 0.5;


        double[][] train_X = {
                {0., 0.},
                {0., 1.},
                {1., 0.},
                {1., 1.},
        };

        int[][] train_Y = {
                {0, 1},
                {1, 0},
                {1, 0},
                {0, 1},
        };

        // construct Dropout
        Dropout classifier = new Dropout(n_in, hidden_layer_sizes, n_out, rng, "ReLU",0.1,true,0.5);

        // train
        classifier.train(n_epochs, train_X, train_Y, dropout, p_dropout, learning_rate);

        // pretest
        if(dropout) classifier.pretest(p_dropout);


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
        test_dropout();
    }
}
