package com.edureka.spark.movielens;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import com.edureka.spark.data.model.movielens.Movie;

import scala.Tuple2;

/**
 * 
 * @author vivek
 *
 */
public class MovieLensDataProcessor implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String inputPath = "/mnt/bigdatapgp/edureka_549997/datasets/movie_lens/ml-10M100K";
	private String outputPath = "output";
	
	private String ratingsFileName = "ratings.dat";
	private String movieDetailsFileName = "movies.dat";
	
	private String delim = "::";
	
	private int topN = 10;
	
	public static void main(String[] args) {

		SparkConf conf = new SparkConf().setAppName("Movie Lens Data Processing");
		
		JavaSparkContext sc = new JavaSparkContext(conf);
		
		int top_n = 20;
		if(args.length >= 3)
			top_n = Integer.parseInt(args[2]);
		MovieLensDataProcessor processor = new MovieLensDataProcessor(args[0], args[1], top_n);
		
		processor.processMovieLensData(sc);
		
	}

	public MovieLensDataProcessor(String inputPath, String outputPath, int top_n) {
		this.inputPath = inputPath;
		this.outputPath = outputPath;
		this.topN = top_n;
	}
	
	public JavaPairRDD<Integer, Movie> loadMoviesData(JavaSparkContext sc){
		// Loading Movies data --> movie_id::title::tag1|tag2...
		JavaRDD<String> moviesFile = sc.textFile(inputPath+"/"+movieDetailsFileName).cache();
		moviesFile = moviesFile.filter(x -> !x.startsWith("movieId"));
		JavaPairRDD<Integer, Movie> moviesRdd = 
				moviesFile.mapToPair(x -> {
					String[] tokens = x.split(delim);
					int movieId = Integer.parseInt(tokens[0]);
					Movie movie = new Movie(movieId, tokens[1]);
					String[] genres = tokens[2].split("\\|");
					List<String> genreList = new ArrayList<>();
					for(String t : genres)
						genreList.add(t);
					movie.setGenres(genreList);

					return new Tuple2<>(movieId, movie);
				}).cache();

		return moviesRdd;
	}
	
	public JavaPairRDD<Integer, Tuple2<Integer, Double>> loadRatingsData(JavaSparkContext sc){
		// Loading ratings data --> UserID::MovieID::Rating::Timestamp
		JavaRDD<String> ratingsFile = sc.textFile(inputPath+"/"+ratingsFileName).cache();
		ratingsFile = ratingsFile.filter(x -> !x.startsWith("userId"));
		// <MovieId, <UserId, Rating>>
		JavaPairRDD<Integer, Tuple2<Integer, Double>> ratingsRdd = 
				ratingsFile.mapToPair(x -> {
					String[] tokens = x.split(delim);
					int userId = Integer.parseInt(tokens[0]);
					int movieId = Integer.parseInt(tokens[1]);
					double rating = Double.parseDouble(tokens[2]);

					return new Tuple2<>(movieId, new Tuple2<>(userId, rating));
				}).cache();
		
		return ratingsRdd;
	}
	
	/**
	 * Answer to Question 1:
	 * To compute the genre wise movie count and print.
	 * Output will be saved under the specified directory in the code.
	 * @param sc
	 * @param moviesRdd
	 */
	public void computeGenreWiseMovieCount(JavaSparkContext sc, 
			JavaPairRDD<Integer, Movie> moviesRdd) {
		// Genre Summary on movies..
		Map<String, Long> genreWiseMovieCount = 
				moviesRdd.flatMapToPair(x -> {

					Movie m = x._2;
					List<Tuple2<Integer, String>> tuples = new ArrayList<>();
					for(String g : m.getGenres()) {
						tuples.add(new Tuple2<>(x._1, g));
					}
					return tuples.iterator();
				}).map(x -> x._2).countByValue();
		List<String> toPrint = new ArrayList<>();
		genreWiseMovieCount.entrySet().forEach(x -> toPrint.add(x.getKey()+","+ x.getValue()));

		sc.parallelize(toPrint).saveAsTextFile(outputPath +"/GenreMovieCount");
	}
	
	/**
	 * Answer to Question 2 and 3:
	 * 2. To compute the rating count for each movie and save the same.
	 * 3. To compute the top n movies based on rating count and print.
	 * Generated output will be saved under the specified directory in the code.
	 * @param sc
	 * @param topN
	 * @param ratingsRdd
	 * @param moviesRdd
	 * @return
	 */
	public JavaPairRDD<Integer, Integer> computeTopNWithRatingCount(JavaSparkContext sc, int topN,
			JavaPairRDD<Integer, Tuple2<Integer, Double>> ratingsRdd,
			JavaPairRDD<Integer, Movie> moviesRdd) {
		// Top N Popular movies based on rating count.
		JavaPairRDD<Integer, Integer> moviesRatingCount = 
				ratingsRdd.mapToPair(x -> new Tuple2<>(x._1,1)).reduceByKey((x,y) -> x+y);
		joinWithMoviesAndPrint(sc, moviesRatingCount, moviesRdd, "RatingCount");

		moviesRatingCount.map(x -> x._1 +"," + x._2).saveAsTextFile(outputPath +"/RatingCount");
		
		return moviesRatingCount;
	}
	
	/**
	 * Answer to Question 3:
	 * To compute the top n movies based on the cumulative rating values.
	 * Generated output will be saved under the specified directory in the code.
	 * @param sc
	 * @param topN
	 * @param ratingsRdd
	 * @param moviesRdd
	 * @return
	 */
	public JavaPairRDD<Integer, Double> computeTopNWithCumulativeRating(JavaSparkContext sc, int topN,
			JavaPairRDD<Integer, Tuple2<Integer, Double>> ratingsRdd,
			JavaPairRDD<Integer, Movie> moviesRdd) {
		JavaPairRDD<Integer, Double> ratingSumRdd = ratingsRdd.mapToPair(x -> new
				Tuple2<>(x._1, x._2._2)).foldByKey(0.0, (x,y) -> x+y );
		joinWithMoviesAndPrint1(sc, ratingSumRdd, moviesRdd, "CumulativeRating");
		
		return ratingSumRdd;
	}
	
	/**
	 * Answer to Question 4:
	 * To compute the top n movies based on the average rating values.
	 * Generated output will be saved under the specified directory in the code.
	 * @param sc
	 * @param topN
	 * @param ratingsRdd
	 * @param moviesRdd
	 * @param ratingCountRdd
	 * @param ratingSumRdd
	 * @return
	 */
	public JavaPairRDD<Integer, Tuple2<Double, Integer>> computeTopNWithMeanRating(JavaSparkContext sc, int topN,
			JavaPairRDD<Integer, Tuple2<Integer, Double>> ratingsRdd,
			JavaPairRDD<Integer, Movie> moviesRdd,
			JavaPairRDD<Integer, Integer> ratingCountRdd,
			JavaPairRDD<Integer, Double> ratingSumRdd) {
		JavaPairRDD<Integer, Tuple2<Double, Integer>> ratingAvgRdd =
				ratingSumRdd.join(ratingCountRdd).mapToPair(x -> new Tuple2<>(x._1,
						new Tuple2<>(x._2._1/(1.0 *x._2._2),x._2._2)));
		
		ratingAvgRdd.map(x -> x._1 + "," + x._2._2+"," +x._2._1).saveAsTextFile(outputPath +"/RatingCountAvg");
		
		joinWithMoviesAndPrint1(sc, ratingAvgRdd.mapToPair(x -> new Tuple2<>(x._1, x._2._1)), moviesRdd, "AvgRating");
		
		return ratingAvgRdd;
	}
	
	public void processMovieLensData(JavaSparkContext sc) {
		
		JavaPairRDD<Integer, Movie> moviesRdd = loadMoviesData(sc);
		JavaPairRDD<Integer, Tuple2<Integer, Double>> ratingsRdd = loadRatingsData(sc);
		
		computeGenreWiseMovieCount(sc, moviesRdd);
		
		JavaPairRDD<Integer, Integer> ratingCountRdd = computeTopNWithRatingCount(sc, 
				topN, ratingsRdd, moviesRdd);
		JavaPairRDD<Integer, Double> ratingSumRdd = computeTopNWithCumulativeRating(sc, 
				topN, ratingsRdd, moviesRdd);
		
		JavaPairRDD<Integer, Tuple2<Double, Integer>> ratingAvgRdd = 
				computeTopNWithMeanRating(sc, topN, ratingsRdd, 
						moviesRdd, ratingCountRdd, ratingSumRdd);
		
		
		/**
		 * Answer to Question 5:
		 * To compute the top n movies based on the mean rating, which also 
		 * has at least 10K ratings.
		 */
		// Filtering for only popular movies with at least 10K ratings..
		joinWithMoviesAndPrint1(sc, ratingAvgRdd.filter(x -> x._2._2 >= 10000).mapToPair(x -> new Tuple2<>(x._1, x._2._1)), moviesRdd, "AvgRating_AtLeast10K");
		
		computeGenreLevelUserPref(sc, 
				moviesRdd, ratingsRdd);
	}
	
	/**
	 * Answer to Question 6: 
	 * To compute the genre level preference for every user. Which is 
	 * a ratio between <user_id, genre_apperance_count/user_rating_count>
	 * @param sc
	 * @param moviesRdd
	 * @param ratingsRdd
	 */
	public void computeGenreLevelUserPref(JavaSparkContext sc, 
			JavaPairRDD<Integer, Movie> moviesRdd, 
			JavaPairRDD<Integer, Tuple2<Integer, Double>> ratingsRdd) {

		JavaPairRDD<Integer, Integer> userRatingCount = 
				ratingsRdd.mapToPair(x -> new Tuple2<>(x._2._1, 1)).reduceByKey((x,y) -> x+y);
		JavaPairRDD<Integer, Movie> userIdMovieRdd =
				ratingsRdd.mapToPair(x -> new Tuple2<>(x._1, x._2._1)).join(moviesRdd)
				.mapToPair(x-> x._2);

		JavaPairRDD<Integer, String> userGenreRdd = 
				userIdMovieRdd.flatMapToPair(x -> {
					int userId = x._1;
					List<String> genres = x._2.getGenres();
					List<Tuple2<Integer, String>> tuples = new ArrayList<>();
					for(String genre : genres) 
						tuples.add(new Tuple2<>(userId, genre));
					return tuples.iterator();
				});

		JavaPairRDD<Integer, Tuple2<String, Integer>> userGenreCountRdd = 
				userGenreRdd.mapToPair(x -> new Tuple2<>(x._1+"_"+x._2, 1)).reduceByKey((x,y) -> x+y)
				.mapToPair(x -> {
					String[] tokens = x._1.split("_");
					int userId = Integer.parseInt(tokens[0]);
					String genre = tokens[1];
					int count = x._2;
					return new Tuple2<Integer, Tuple2<String, Integer>>(userId, new Tuple2<>(genre, count));
				});
		JavaPairRDD<Integer, Tuple2<String, Double>> userGenrePrefScoreRdd = 
				userGenreCountRdd.join(userRatingCount).mapToPair(x -> {
					int ratingCount = x._2._2;
					int genreCount = x._2._1._2;
					return new Tuple2<>(x._1, new Tuple2<>(x._2._1._1, genreCount/(ratingCount*1.0)));
				});
		
		userGenrePrefScoreRdd.map(x -> x._1 +"," + x._2._1 + "," + x._2._2)
			.saveAsTextFile(outputPath + "/User-Genre-PrefScore");
	}
	
	private void joinWithMoviesAndPrint(JavaSparkContext sc, 
			JavaPairRDD<Integer, Integer> moviesStatRdd, JavaPairRDD<Integer, Movie> moviesRdd,
			String type) {
		List<Tuple2<Integer, Tuple2<Integer, Movie>>>  top20Movies = 
				moviesStatRdd.join(moviesRdd).takeOrdered(20, new SerializableComparator());
		JavaRDD<Tuple2<Integer, Tuple2<Integer, Movie>>> top20MoviesRdd = 
				sc.parallelize(top20Movies);
		
		top20MoviesRdd.map(x -> x._1 +"," + x._2._1 + "," + x._2._2.getTitle())
			.saveAsTextFile(outputPath+"/Top20-"+type);;
		
	}
	
	private void joinWithMoviesAndPrint1(JavaSparkContext sc, JavaPairRDD<Integer, Double> moviesStatRdd, JavaPairRDD<Integer, Movie> moviesRdd, String type) {
		List<Tuple2<Integer, Tuple2<Double, Movie>>>  top20Movies = 
				moviesStatRdd.join(moviesRdd).takeOrdered(20, new SerializableComparatorDouble());

		JavaRDD<Tuple2<Integer, Tuple2<Double, Movie>>> top20MoviesRdd = 
				sc.parallelize(top20Movies);
		
		top20MoviesRdd.map(x -> x._1 +"," + x._2._1 + "," + x._2._2.getTitle())
			.saveAsTextFile(outputPath+"/Top20-"+type);;

	}
	
	static class SerializableComparator implements Serializable, Comparator<Tuple2<Integer, Tuple2<Integer, Movie>>> {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(Tuple2<Integer, Tuple2<Integer, Movie>> o1, Tuple2<Integer, Tuple2<Integer, Movie>> o2) {
			return o2._2._1.compareTo(o1._2._1);
		}
		
	}

	static class SerializableComparatorDouble implements Serializable, Comparator<Tuple2<Integer, Tuple2<Double, Movie>>> {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		
		@Override
		public int compare(Tuple2<Integer, Tuple2<Double, Movie>> o1, Tuple2<Integer, Tuple2<Double, Movie>> o2) {
			// TODO Auto-generated method stub
			return o2._2._1.compareTo(o1._2._1);
		}
		
	}
}
