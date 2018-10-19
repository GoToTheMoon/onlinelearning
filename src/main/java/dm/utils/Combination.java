package dm.utils;

import java.util.ArrayList;

public class Combination {

    public static ArrayList<Pair<String, String>> combine(String[] columns,int k)
    {
        ArrayList<Pair<String, String>> comb = new ArrayList<Pair<String, String>>();

        for(ArrayList<Integer> pair:combine(columns.length-1,k))
        {
            int left = pair.get(0);
            int right = pair.get(1);

            comb.add(new Pair<String,String>(columns[left],columns[right]));
        }
        return comb;
    }


    private static ArrayList<ArrayList<Integer>> combine(int n, int k)
    {
        ArrayList<ArrayList<Integer>> res = new ArrayList<ArrayList<Integer>>();
        if(n <= 0||n < k)
            return res;

        ArrayList<Integer> item = new ArrayList<Integer>();
        dfs(n,k,1,item, res);//because it need to begin from 1

        return res;
    }

    private static void dfs(int n, int k, int start, ArrayList<Integer> item, ArrayList<ArrayList<Integer>> res)
    {
        if(item.size()==k){
            //because item is ArrayList<T> so it will not disappear from stack to stack
            res.add(new ArrayList<Integer>(item));
            return;
        }
        for(int i=start;i<=n;i++){
            item.add(i);
            dfs(n,k,i+1,item,res);
            item.remove(item.size()-1);
        }
    }
}
