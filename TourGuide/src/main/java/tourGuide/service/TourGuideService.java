package tourGuide.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tourGuide.configuration.TourGuideInitialization;
import tourGuide.domain.location.Attraction;
import tourGuide.domain.location.Location;
import tourGuide.domain.location.NearbyAttraction;
import tourGuide.domain.location.VisitedLocation;
import tourGuide.domain.tripdeal.Provider;
import tourGuide.domain.user.User;
import tourGuide.domain.user.UserPreferences;
import tourGuide.domain.user.UserReward;
import tourGuide.tracker.Tracker;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);

	private final RewardsService rewardsService;
	public final Tracker tracker;
	boolean testMode = true;

	//@Autowired
	private TourGuideInitialization init = new TourGuideInitialization();

	public TourGuideService(RewardsService rewardsService) {
		this.rewardsService = rewardsService;

		if(testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			init.initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this, rewardsService);
		addShutDownHook();
	}

	public User getUser(String userName) {
		return init.getInternalUserMap().get(userName);
	}

	public List<User> getAllUsers() {
		return  init.getInternalUserMap().values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if(!init.getInternalUserMap().containsKey(user.getUserName())) {
			init.getInternalUserMap().put(user.getUserName(), user);
		}
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = user.getLastVisitedLocation() ;
		/*
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ?
			user.getLastVisitedLocation() :
			trackUserLocation(user);
		*/
		return visitedLocation;
	}

	public HashMap<String, Location> getAllCurrentLocations() {
		HashMap<String, Location> allCurrentLocations = new HashMap<>();
		List<User> allUsers = getAllUsers();
		allUsers.forEach(user -> allCurrentLocations.put(user.getUserId().toString(), user.getLastVisitedLocation().location));
		return allCurrentLocations;
	}

	public Flux<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();

		List<Provider> providers = new ArrayList<>();

		logger.debug("Request getTripDeals build");

		String requestURI = "/getPrice?apiKey=" + TourGuideInitialization.getTripPricerApiKey() + "&attractionId=" + user.getUserId() + "&adults=" + user.getUserPreferences().getNumberOfAdults() + "&children=" + user.getUserPreferences().getNumberOfChildren() + "&nightsStay=" + user.getUserPreferences().getTripDuration() + "&rewardsPoints=" + cumulatativeRewardPoints;

		WebClient webClient = WebClient.create("http://localhost:8083");

		/*
		String result = webClient.get()
				.uri(requestURI)
				.retrieve()
				.bodyToMono(String.class)
				.map(s -> { return
								webClient.get()
										.uri(requestURI)
										.retrieve()
										.bodyToMono(String.class)
										.block();
						}
				)
				.block();
		*/

		/*
		Mono<ClientResponse> result = webClient.get()
				.uri(requestURI)
				.accept(MediaType.APPLICATION_JSON)
				.exchange();
		*/

		Flux<Provider> result = webClient.get()
				.uri(requestURI)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToFlux(Provider.class)
				.doOnComplete(()->System.out.println("SUCES"))
				//.doOnError((a)->System.out.println("EROR"))
				//.doOnComplete( (rs) -> user.setTripDeals(rs.co )
				;
				//.doOnComplete( () -> user.setTripDeals(result.collectList()) );

		//result.doOnNext(user-> user.setTripDeals(providers) );
		//providers = result.toStream().collect(Collectors.toList());

		/*
		String result3 = webClient.get()
				.uri(requestURI)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToFlux(Provider.class).toString();
		*/
		//String res = result.subscribe().toString();//flatMap(res -> res.bodyToMono (String.class)).block();

		//String r = result.flatMap(res -> res.bodyToMono (String.class)).block();

		System.out.println(result);

		/*
		ObjectMapper mapper = new ObjectMapper();
		try {
			//providers = mapper.readValue((JsonParser) providers, new TypeReference<List<Provider>>(){ });
			providers = mapper.readValue(result, new TypeReference<List<Provider>>(){ });
		} catch (IOException e) {
			e.printStackTrace();
		}
		*/

		/*
		HttpClient client = HttpClient.newHttpClient();
		String requestURI = "http://localhost:8083/getPrice?apiKey=" + TourGuideInitialization.getTripPricerApiKey() + "&attractionId=" + user.getUserId() + "&adults=" + user.getUserPreferences().getNumberOfAdults() + "&children=" + user.getUserPreferences().getNumberOfChildren() + "&nightsStay=" + user.getUserPreferences().getTripDuration() + "&rewardsPoints=" + cumulatativeRewardPoints;

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(requestURI))
				.GET()
				.build();
		try {
			HttpResponse <String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			logger.debug("Status code = " + response.statusCode());
			logger.debug("Response Body = " + response.body());
			ObjectMapper mapper = new ObjectMapper();
			providers = mapper.readValue(response.body(), new TypeReference<List<Provider>>(){ });
		} catch (IOException | InterruptedException e) {
			logger.error(e.toString());
			e.printStackTrace();
		}
		*/
		user.setTripDeals(providers);
		return result;
	}

	public Mono<VisitedLocation> trackUserLocation(User user) {
		logger.debug("Track Location - Thread : " + Thread.currentThread().getName() + " - User : " + user.getUserName());

		//VisitedLocation visitedLocation = new VisitedLocation();

		logger.debug("Request getUserLocation build");
		WebClient webClient = WebClient.create("http://localhost:8081");
		String requestURI = "/getUserLocation?userId=" + user.getUserId();
		System.out.println(requestURI);

		Mono<VisitedLocation> result = webClient.get()
				//.uri(requestURI)
				.uri("/getUserLocation?userId="+ user.getUserId())
				//.uri(uriBuilder -> uriBuilder
				//				.path("/getUserLocation/")
				//				.queryParam("userId", user.getUserId().toString())
				//				.build())
				//.accept(MediaType.APPLICATION_JSON)
				//.exchange()
				//.then(response -> response.bodyToMono(VisitedLocation.class))
				.retrieve()
				.bodyToMono(VisitedLocation.class)
				.doOnSuccess( (rs) -> user.addToVisitedLocations(rs) )
				.doOnSuccess((r)->System.out.println("SUCES"))
				.doOnError((r)->System.out.println("EROR"))
				//.flatMap()map( () -> user.addToVisitedLocations(result) )
				;
		/*
		HttpClient client = HttpClient.newHttpClient();
		String requestURI = "http://localhost:8081/getUserLocation?userId=" + user.getUserId();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(requestURI))
				//.header("userId", user.getUserId().toString())
				.GET()
				.build();
		try {
			HttpResponse <String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			logger.debug("Status code = " + response.statusCode());
			logger.debug("Response Body = " + response.body());
			ObjectMapper mapper = new ObjectMapper();
			visitedLocation = mapper.readValue(response.body(), VisitedLocation.class);
		} catch (IOException | InterruptedException e) {
			logger.error(e.toString());
			e.printStackTrace();
		}
		user.addToVisitedLocations(visitedLocation);

		return visitedLocation;
		*/
		return result;

	}

	public List<NearbyAttraction> getNearByAttractions(VisitedLocation visitedLocation, User user) {
		List<NearbyAttraction> nearbyAttractions = new ArrayList<>();
		List<Attraction> allAttractions = rewardsService.getAllAttractions();
		//List<Attraction> allAttractions = gpsService.getAttractions();
		TreeMap<Double, NearbyAttraction> treeAttractionDistance = new TreeMap<>();

		allAttractions.forEach(attraction -> treeAttractionDistance.put(rewardsService.getDistance(attraction, visitedLocation.location), new NearbyAttraction(attraction.attractionName, new Location(attraction.latitude, attraction.longitude), visitedLocation.location, rewardsService.getDistance(attraction, visitedLocation.location), rewardsService.getRewardPoints(attraction, user))));
		nearbyAttractions = treeAttractionDistance.values().stream()
															.limit(5)
															.collect(Collectors.toList());

		return nearbyAttractions;
	}

	public UserPreferences getUserPreferences(User user) {
		UserPreferences userPreferences = user.getUserPreferences();
		return userPreferences;
	}

	public UserPreferences postUserPreferences(User user, UserPreferences userPreferences) {
		user.setUserPreferences(userPreferences);
		return userPreferences;
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() { 
		      public void run() {
		        tracker.stopTracking();
		      } 
		    }); 
	}
}
