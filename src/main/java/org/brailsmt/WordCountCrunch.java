package org.brailsmt;

import org.apache.crunch.DoFn;
import org.apache.crunch.Emitter;
import org.apache.crunch.PTable;
import org.apache.crunch.Pair;
import org.apache.crunch.Pipeline;
import org.apache.crunch.fn.Aggregators;
import org.apache.crunch.impl.mr.MRPipeline;
import org.apache.crunch.impl.mr.plan.PlanningParameters;
import org.apache.crunch.lib.Sort;
import org.apache.crunch.types.writable.Writables;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 *  @author Michael Brailsford (mb013619)
 */
public class WordCountCrunch extends Configured implements Tool {

    public static void main(String ... args) throws Exception {
        ToolRunner.run(new Configuration(), new WordCountCrunch(), args);
    }

    public static class Splitter extends DoFn<String, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String in, Emitter<String> emitter) {
            for(String _word: in.split("\\s+")) {
                char[] word = new char[_word.length()];
                char[] cs = _word.toCharArray();
                int i = 0;
                for(char c: cs) {
                    if(Character.isLetter(c) && c != ' ') {
                        word[i++] = c;
                    }
                }

                if(i > 0 && i <= word.length) {
                    emitter.emit(String.valueOf(word).toLowerCase().substring(0, i));
                }
            }
        }
    }

    public static class WordCounter extends DoFn<String, Pair<String, Integer>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String word, Emitter<Pair<String, Integer>> emitter) {
            emitter.emit(Pair.of(word, 1));
        }
    }

    public static class SwapKeyValues extends DoFn<Pair<String, Integer>, Pair<Integer, String>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(Pair<String, Integer> input, Emitter<Pair<Integer, String>> emitter) {
            emitter.emit(Pair.of(input.second(), input.first()));
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        Pipeline pipeline = new MRPipeline(WordCountCrunch.class, getConf());

        PTable<Integer, String> wordCount = 
            Sort.sort(pipeline.readTextFile(args[0])
                              .parallelDo(new Splitter(), Writables.strings())
                              .parallelDo(new WordCounter(), Writables.tableOf(Writables.strings(), Writables.ints()))
                              .groupByKey()
                              .combineValues(Aggregators.SUM_INTS())
                              .parallelDo(new SwapKeyValues(), Writables.tableOf(Writables.ints(), Writables.strings())));

        Sort.sort(wordCount);
        pipeline.writeTextFile(wordCount, "counts.txt");
        pipeline.done();

        String dot = pipeline.getConfiguration().get(PlanningParameters.PIPELINE_PLAN_DOTFILE);  
            
        System.out.println(dot);

        return 0;
    }

}
