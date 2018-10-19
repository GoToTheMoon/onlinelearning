package dm.interfaces;
import java.util.Map;


public interface FTRL
{
    double predict(Map<String, String> X);

    double fit(Map<String,String> X,double y);



}


