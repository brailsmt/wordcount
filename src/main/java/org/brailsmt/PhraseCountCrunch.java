package org.brailsmt;

import org.apache.crunch.DoFn;
import org.apache.crunch.Emitter;
import org.apache.crunch.PCollection;
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

import com.google.common.base.Joiner;
import com.google.common.collect.EvictingQueue;

/**
 *  @author Michael Brailsford (mb013619)
 */
public class PhraseCountCrunch extends Configured implements Tool {

    public static class PhraseSplitter extends DoFn<String, String> {
        private static final long serialVersionUID = 1L;

        private int numWords;
        private transient Joiner joiner;

        public PhraseSplitter() {
            this(1);
        }

        public PhraseSplitter(int n) {
            numWords = n;
        }

        @Override
        public void initialize() {
            joiner = Joiner.on(" ");
        }

        @Override
        public void process(String in, Emitter<String> emitter) {
            EvictingQueue<String> phrasebuf = EvictingQueue.create(numWords);
            for (String _word : in.split("\\s+")) {
                char[] word = new char[_word.length()];
                char[] cs = _word.toCharArray();
                int i = 0;
                for (char c : cs) {
                    if (Character.isLetter(c) && c != ' ') {
                        word[i++] = c;
                    }
                }

                if(i == 0)
                    continue;

                phrasebuf.add(String.valueOf(word).substring(0, i));
                if(phrasebuf.remainingCapacity() == 0) {
                    String phrase = joiner.join(phrasebuf);
                    emitter.emit(phrase.toLowerCase());
                }
            }
        }
    }

    public static void main(String... args) throws Exception {
        ToolRunner.run(new Configuration(), new PhraseCountCrunch(), args);
//        PhraseSplitter splitter = new PhraseSplitter(Integer.parseInt(args[0]));
//        InMemoryEmitter<String> emitter = new InMemoryEmitter<String>();
//        splitter.process("This is a sentence with some phrases", emitter);
//        for(String phrase: emitter.getOutput()) {
//            System.out.println(phrase);
//        }
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
        Pipeline pipeline = new MRPipeline(PhraseCountCrunch.class, getConf());

        PCollection<String> lines = pipeline.readTextFile(args[0]);
        for(int i = 0; i < Integer.parseInt(args[1]); i++) {
            PTable<Integer, String> wordCount = 
                Sort.sort(lines.parallelDo(new PhraseSplitter(i+1), Writables.strings())
                        .parallelDo(new WordCounter(), Writables.tableOf(Writables.strings(), Writables.ints()))
                        .groupByKey()
                        .combineValues(Aggregators.SUM_INTS())
                        .parallelDo(new SwapKeyValues(), Writables.tableOf(Writables.ints(), Writables.strings())));

            pipeline.writeTextFile(wordCount, "counts" + i);
        }
        pipeline.done();

        String dot = pipeline.getConfiguration().get(PlanningParameters.PIPELINE_PLAN_DOTFILE);  
            
        System.out.println(dot);

        return 0;
    }

}

/*
    brew install docker boot2docker
    boot2docker init -m 4096
    boot2docker start
    $(boot2docker shellinit)
    ./start-build-env.sh
 */
