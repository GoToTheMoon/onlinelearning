package dm.interfaces;

public interface FNNInterface {
    double predict(String[] value) throws Exception;
    void fit(String value) throws Exception;
}