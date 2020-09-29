package tourGuide.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tourGuide.configuration.TourGuideInitialization;
import tourGuide.domain.location.NearbyAttraction;
import tourGuide.domain.location.Attraction;
import tourGuide.domain.location.Location;
import tourGuide.domain.location.VisitedLocation;
import tourGuide.tracker.Tracker;
import tourGuide.domain.user.User;
import tourGuide.domain.user.UserPreferences;
import tourGuide.domain.user.UserReward;
import tourGuide.domain.tripdeal.Provider;

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
		//String res = result.  subscribe().  toString();//    flatMap(res -> res.bodyToMono (String.class)).block();

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

		VisitedLocation visitedLocation = new VisitedLocation();

		logger.debug("Request getUserLocation build");
		WebClient webClient = WebClient.create("http://localhost:8083");
		String requestURI = "http://localhost:8081/getUserLocation?userId=" + user.getUserId();
		Mono<VisitedLocation> result = webClient.get()
				.uri(requestURI)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToMono(VisitedLocation.class)
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
		/*
		List<Attraction> allAttractions = new ArrayList<>();

		logger.debug("Request getNearByAttractions build");
		HttpClient client = HttpClient.newHttpClient();
		String requestURI = "http://localhost:8081/getAttractions";
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(requestURI))
				.GET()
				.build();
		try {
			HttpResponse <String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			logger.debug("Status code = " + response.statusCode());
			logger.debug("Response Body = " + response.body());
			ObjectMapper mapper = new ObjectMapper();
			allAttractions = mapper.readValue(response.body(), new TypeReference<List<Attraction>>(){ });
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		*/
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
