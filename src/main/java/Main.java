import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {


    public static void main(String[] args) {
        SparkConf conf = new SparkConf().setAppName("Workshop").setMaster("local");
        JavaSparkContext sc = new JavaSparkContext(conf);


        Scanner scan = null;
        try {
            scan = new Scanner(new File("french-stopwords"));
            ArrayList<String> list = new ArrayList<String>();
            while (scan.hasNext()){
                list.add(scan.next());
            }
            scan.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        //QUESTION 1
            //RECUPERER LES FICHIERS CF
        String path = Paths.get(Main.class.getResource("EVC-TXT/cf").getPath()).toString();
            //CREER RDD
        JavaRDD<String> lines = sc.textFile(path);

        //QUESTION 2
            //Compter les occurences des mots
        JavaPairRDD<String, Integer> counts = lines
                //supprime les espaces du compte de mots
                .flatMap(s -> Arrays.asList(s.toLowerCase().replaceAll("[^A-Za-z\\-àâäéèêëïîôöùûüÿçæœ]+"," ").trim().split("\\s+")))
                .filter(s -> !(s.isEmpty()))
                .mapToPair(word -> new Tuple2<>(word, 1))
                .reduceByKey((a, b) -> a + b);
            //Tri des mots par ordre décroissant des apparitions
        counts = counts.mapToPair(s -> s.swap())
                .sortByKey(false)
                .mapToPair(s -> s.swap());
            //Affichage
        counts.foreach(data -> {
            System.out.println("word="+data._1()+" iteration="+data._2());});

        System.out.println("---------------------------------------------------------------");
        //QUESTION 3
        String pathStopWords = Paths.get(Main.class.getResource("french-stopwords.txt").getPath()).toString();
        JavaRDD<String> stopwords = sc.textFile(pathStopWords);
        JavaPairRDD<String, Integer> countsWithoutStopWords = counts.subtractByKey(stopwords.mapToPair(word -> new Tuple2<>(word, 1)));
            //Tri des mots par ordre décroissant des apparitions
        countsWithoutStopWords = countsWithoutStopWords.mapToPair(s -> s.swap())
                .sortByKey(false)
                .mapToPair(s -> s.swap());
            //Affichage
        countsWithoutStopWords.foreach(data -> {System.out.println("word="+data._1()+" iteration="+data._2());});

        //QUESTION 4
        System.out.println("--------------------------------------");
        System.out.println("----------QUESTION-4------------------");
        List countsTop10 = countsWithoutStopWords.take(10);
        System.out.println("Top 10 des mots :");
        countsTop10.forEach(data -> {System.out.println(data);});
    }





}
