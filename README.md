# Rapport mini-projet BigData

## Julien Bailly - Benoît Holzer

###Utilisation :

Il est possible de préciser 4 arguments au programme dans cette ordre :

args[0] : correspond au dossier à parcourir (valeur possible: cp ou cf, par défaut : cf).

args[1] : correspond au top-k des mots que l'on souhaite afficher (doit être un int, par défaut : 10).

args[2] : correspond à la valeur minSupport (doit être un double compris entre 0.0 et 1.0, par défaut : 0.5).

args[3] : correspond à la valeur minConfidence (doit être un double compris entre 0.0 et 1.0, par défaut : 0.5).

----
Exemple d'utilisation pour lancer le programme :

java Main.class cf 7 0.3 0.8


correspond à lancer la recherche sur le dossier CF avec les 7 meilleurs résultat, un minSupport de 0.3 et un minConfidence de 0.8 .

---
###Pour les questions 1 à 4 :

Nous avons dans un premier temps récupéré les documents .txt et les avons stockés dans une JavaRDD.

Ensuite nous avons modifié la casse afin de passer les caractères en minuscule afin de pouvoir compter ensemble les mots apparraissant avec une majuscule et sans (exemple : "Bonjour" et "bonjour").

Puis nous avons remplacé (grâce à un replaceAll() ) les caractères qui n'entre pas dans la prise en compte du comptage de mots par des espaces, c'est à dire les apostrophes, les virgules, les points, les numéros, les slashs et autres caractères spéciaux afin d'ensuite faire un split() sur la chaine à chaque espace.

Nous avons ainsi séparé tous les mots les uns des autres. Il a été nécessaire de faire un filter() sur les caractères vides afin de les supprimer du comptage.

Dans le but de réaliser le comptage des mots nous avons transformer notre JavaRDD en JavaPairRDD contenant pour chaque enregistrement le mot en question et la valeur numérique 1.
Ensuite, grâce à la méthode reduceByKey() nous avons réunis les tuples ayant la même clé, le même mot, en additionant leur valeur numérique pour ainsi finir avec le compte exacte de chaque mot dans le fichier.

```java
//QUESTION 2
    //Compter les occurences des mots   
     JavaPairRDD<String, Integer> counts = lines
             //supprime les espaces du compte de mots
             .flatMap(s -> Arrays.asList(s.toLowerCase().replaceAll("[^A-Za-z\\-àâäéèêëïîôöùûüÿçæœ]+"," ")
                     .trim()
                     .split("\\s+")).iterator())
             .filter(s -> !(s.isEmpty()))
             .mapToPair(word -> new Tuple2<>(word, 1))
             .reduceByKey((a, b) -> a + b);
```

Pour supprimer de la liste les french-stopwords nous avons utilisé la méthode substractByKey en utilisant une JavaRDD des stopwords préalablement établi.
Pour afficher les résultats il a fallu utiliser la méthode forEach() et la méthode take(10) nous a permis d'utiliser seulement les 10 premiers résultats.

```java
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
```
---
###Pour les questions 5 à 10 :

La première étape a été de récupérer les fichiers à traiter dont le chemin d'accès est défini par la variable path et de les stockers dans une List de Row.
```java
File[] files;
File f = new File(path);
files = f.listFiles();
List<Row> listRow = new ArrayList();
```
Ensuite nous avons crée une JavaRDD pour chaque fichier du dossier. Chaque RDD subit les mêmes transformations que pour les questions 1 à 4 du projet puis est ajoutée à notre List de Row appelée listRow.

```java
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
```
Il a fallu ensuite créer notre DataFrame et pour cela nous avons d'abord initialiser un objet StructType, schema. Notre Dataset est ensuite crée avec les valeurs de notre listRow.

```java
StructType schema = new StructType(new StructField[]{ new StructField(
        "items", new ArrayType(DataTypes.StringType, true), false, Metadata.empty())
});

Dataset<Row> itemsDF = spark.createDataFrame(listRow, schema);
```

Nous avons défini des valeurs de bases pour le minSupp et le minConf à 0.5 et laissez la possibilité à l'utilisateur de choisir ses valeurs grâce aux 3e et 4e arguments du programme s'il le souhaite.
Un objet FPGrowthModel a été initialisé, le minSupport et le minConfidence ont vu leur valeur affectée.

Chaque pattern qui apparait plus de (minSupport * taille des données) seront ajouté au frequentItemset.

La valeur minConfidence n'affecte pas le frequentItemset, mais affectera les règles d'associations.

```java
double minSupp = 0.5;
double minConf = 0.5;

if(args.length >= 4){
    minSupp = Double.parseDouble(args[2]);
    minConf = Double.parseDouble(args[3]);
}

FPGrowthModel model = new FPGrowth()
        .setItemsCol("items")
        //QUESTION 7
        .setMinSupport(minSupp) //JOUER AVEC VALEUR
        //QUESTION 9
        .setMinConfidence(minConf) //JOUER AVEC VALEUR
        .fit(itemsDF);
```
Les lignes suivantes permettent l'affichage des données récoltées par le modèle. Les arguments de la méthode show() correspondent au nombre de résultat que l'on souhaite affiché, laissez ici libre de choix à l'utilisateur, et si l'on souhaite affiché les lignes en entier il faut indiquer false en 2e argument.
args[0] correspond au choix de l'utilisateur pour choisir le top-k qu'il souhaite afficher, k étant un entier supérieur à 0.

```java
int topk = 10;
if(args.length >= 0){
 topk = Integer.parseInt(args[1]);
}

// Display frequent itemsets. args[0] is top-k
model.freqItemsets().show(topk,false);

// Display generated association rules.
model.associationRules().show(topk, false);

// transform examines the input items against all the association rules and summarize the
// consequents as prediction
model.transform(itemsDF).show(topk, false);

```

