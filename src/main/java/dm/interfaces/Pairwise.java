package dm.interfaces;

import dm.utils.Pair;

import java.util.List;
import java.util.Map;

public interface Pairwise {
    void fit( List<Pair<Map<String,String>,Double>> samples);
}
