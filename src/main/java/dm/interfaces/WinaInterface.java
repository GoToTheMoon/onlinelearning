package dm.interfaces;

import org.json.JSONObject;
import org.json.JSONString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public interface WinaInterface {

    String predictAll(JSONObject Json) throws Exception;

    void reload_id(HashMap<String,HashMap<String,List>> id);

    void reload_model(String filePath) throws Exception;

}