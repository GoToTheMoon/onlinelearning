package dm.utils;
import java.util.Comparator;


public class StringDoublePairComparator implements Comparator<Pair<String,Double>>
{
    @Override
    public int compare(Pair<String,Double> p1, Pair<String,Double> p2)
    {
        if(p1==null || p2==null)
        {
            return 0;
        }
        else if(p1.getRight() == p2.getRight())
        {
            return 0;
        }
        else if(p1.getRight()>p2.getRight())
        {
            return 1;
        }
        else
        {
            return -1;
        }
    }
}
