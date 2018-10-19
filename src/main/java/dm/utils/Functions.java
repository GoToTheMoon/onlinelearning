package dm.utils;

public class Functions {

    public static double sigmoid(double wTx) {
        wTx = java.lang.Math.max(java.lang.Math.min(wTx, 35.), -35.);
        return 1. / (1. + java.lang.Math.exp(-wTx));
    }

    public static double logLoss(double p,double y)
    {
        p = Math.max(Math.min(p, 1. - 10e-15), 10e-15);
        if(y>=1.0)
        {
            return -Math.log(p);
        }
        else
        {
            return -Math.log(1. - p);
        }
    }
}
