import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.ml.fpm.FPGrowth;
import org.apache.spark.ml.fpm.FPGrowthModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.*;
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
        SparkSession spark = SparkSession
                .builder()
                .appName("SparkSessionExample")
                .getOrCreate();

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
            //RECUPERER LES FICHIERS CF ou CP
        String path = Paths.get(Main.class.getResource("EVC-TXT/cf").getPath()).toString();
        if(args.length == 2){
            switch (args[1]){
                case "CP":
                case "cp":
                    path = Paths.get(Main.class.getResource("EVC-TXT/cp").getPath()).toString();
                    break;
                case "CF":
                case "cf":
                    path = Paths.get(Main.class.getResource("EVC-TXT/cf").getPath()).toString();
                    break;
                default:
                    path = Paths.get(Main.class.getResource("EVC-TXT/cf").getPath()).toString();
                    break;
            }
        }
            //CREER RDD
        JavaRDD<String> lines = sc.textFile(path);

        //QUESTION 2
            //Compter les occurences des mots
        JavaPairRDD<String, Integer> counts = lines
                //supprime les espaces du compte de mots
                .flatMap(s -> Arrays.asList(s.toLowerCase().replaceAll("[^A-Za-z\\-àâäéèêëïîôöùûüÿçæœ]+"," ")
                        .trim()
                        .split("\\s+")).iterator())
                .filter(s -> !(s.isEmpty()))
                //.flatMap(s -> s.iterator())
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

        //PARTIE 2
        File[] files;
        File f = new File("src/main/resources/EVC-TXT/cf");
        files = f.listFiles();
        List<Row> listRow = new ArrayList();
        int i = 0;
        System.out.println(files.length);
        for(File file: files){
            //QUESTION 5
            JavaRDD<String> rdd = sc.textFile(file.getPath());
            rdd = rdd.flatMap(s -> Arrays.asList(s.toLowerCase().replaceAll("[^A-Za-z\\-àâäéèêëïîôöùûüÿçæœ]+"," ")
                    .trim()
                    .split("\\s+")).iterator())
                    .filter(s -> !(s.isEmpty()))
                    .subtract(stopwords); //QUESTION 6
            listRow.add(RowFactory.create(rdd.distinct().take((int)rdd.distinct().count())));
        }

        StructType schema = new StructType(new StructField[]{ new StructField(
                "items", new ArrayType(DataTypes.StringType, true), false, Metadata.empty())
        });

        Dataset<Row> itemsDF = spark.createDataFrame(listRow, schema);

        FPGrowthModel model = new FPGrowth()
                .setItemsCol("items")
                //QUESTION 7
                .setMinSupport(0.8) //JOUER AVEC VALEUR
                //QUESTION 9
                .setMinConfidence(0.6) //JOUER AVEC VALEUR
                .fit(itemsDF);

        // Display frequent itemsets. args[0] is top-k
        model.freqItemsets().show(Integer.parseInt(args[0]),false);

        // Display generated association rules.
        model.associationRules().show(Integer.parseInt(args[0]), false);

        // transform examines the input items against all the association rules and summarize the
        // consequents as prediction
        model.transform(itemsDF).show(Integer.parseInt(args[0]), true);


    }





}
