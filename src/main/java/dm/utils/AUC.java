package dm.utils;


public class AUC {

    /**
     * Caulculate AUC for binary classifier.
     * @param label The sample labels
     * @param prediction The posterior probability of positive class.
     * @return AUC
     */

    public static double measure(double[] label, double[] prediction,double subSamplingRate)
    {
        // for large sample size, overflow may happen for pos * neg.
        // switch to double to prevent it.

        double pos = 0;
        double neg = 0;

        for (int i = 0; i < label.length; i++) {

            if (label[i] == 0) {
                neg++;
            } else if (label[i] == 1) {
                pos++;
            } else {
                throw new IllegalArgumentException("AUC is only for binary classification. Invalid label: " + label[i]);
            }
        }

        //calibration practical lessons learned from predicting ctr at facebook
        //p' = p/(p+(1-p)/subSamplingRate)
        //subSamplingRate = 0.1
        //a[:,1] = a[:,1]/(a[:,1]+(1-a[:,1])/subsampling)
        if(subSamplingRate<1.0)
        {
            for (int i = 0; i < prediction.length; i++)
            {
                double v = prediction[i];
                prediction[i] = v/(v+(1-v)/subSamplingRate);
            }
        }

        QuickSort.sort(prediction, label);
        double[] rank = new double[label.length];

        for (int i = 0; i < prediction.length; i++)
        {
            if (i == prediction.length - 1 || prediction[i] != prediction[i+1]) {
                rank[i] = i + 1;
            } else
            {
                int j = i + 1;
                for (; j < prediction.length && prediction[j] == prediction[i]; j++);
                double r = (i + 1 + j) / 2.0;
                for (int k = i; k < j; k++) rank[k] = r;
                i = j - 1;
            }
        }


        double auc = 0.0;
        for (int i = 0; i < label.length; i++) {
            if (label[i] == 1)
                auc += rank[i];
        }

        auc = (auc - (pos * (pos+1) / 2.0)) / (pos * neg);
        return auc;
    }
}