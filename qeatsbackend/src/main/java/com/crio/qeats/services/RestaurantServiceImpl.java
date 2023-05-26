
/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.services;

import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.repositoryservices.RestaurantRepositoryService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class RestaurantServiceImpl implements RestaurantService {

  private final Double peakHoursServingRadiusInKms = 3.0;
  private final Double normalHoursServingRadiusInKms = 5.0;

  @Autowired
  private RestaurantRepositoryService restaurantRepositoryService;
  
  public GetRestaurantsResponse response = new GetRestaurantsResponse();

  @Override
  public GetRestaurantsResponse findAllRestaurantsCloseBy(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
    if (isPeakHour(currentTime)) {
      return new GetRestaurantsResponse(
          restaurantRepositoryService.findAllRestaurantsCloseBy(getRestaurantsRequest.getLatitude(),
              getRestaurantsRequest.getLongitude(), currentTime, 3.0));
    } else {
      return new GetRestaurantsResponse(
          restaurantRepositoryService.findAllRestaurantsCloseBy(getRestaurantsRequest.getLatitude(),
              getRestaurantsRequest.getLongitude(), currentTime, 5.0));
    }
  }


  private boolean isPeakHour(LocalTime currentTime) {
    int hour = currentTime.getHour();
    if (hour >= 8 && hour <= 10 || hour >= 13 && hour <= 14 || hour >= 19 && hour <= 21) {
      return true;
    } else {
      return false;
    }
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Implement findRestaurantsBySearchQuery. The request object has the search string.
  // We have to combine results from multiple sources:
  // 1. Restaurants by name (exact and inexact)
  // 2. Restaurants by cuisines (also called attributes)
  // 3. Restaurants by food items it serves
  // 4. Restaurants by food item attributes (spicy, sweet, etc)
  // Remember, a restaurant must be present only once in the resulting list.
  // Check RestaurantService.java file for the interface contract.

  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQuery(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
   
    Double latitude = getRestaurantsRequest.getLatitude();
    Double longitude = getRestaurantsRequest.getLongitude();
    String searchFor = getRestaurantsRequest.getSearchFor();

    if (searchFor.equals("")) {
      List<Restaurant> restaurantList = new ArrayList<Restaurant>() {};
      response.setRestaurants(restaurantList);
      return response;
    } else if ((isPeakHour(currentTime))) {
      List<Restaurant> restaurantList = restaurantRepositoryService.findRestaurantsByName(latitude,
          longitude, searchFor, currentTime, peakHoursServingRadiusInKms);
      restaurantList.addAll(restaurantRepositoryService.findRestaurantsByAttributes(latitude,
          longitude, searchFor, currentTime, peakHoursServingRadiusInKms));
      restaurantList.addAll(restaurantRepositoryService.findRestaurantsByItemName(latitude,
          longitude, searchFor, currentTime, peakHoursServingRadiusInKms));
      restaurantList.addAll(restaurantRepositoryService.findRestaurantsByItemAttributes(latitude,
          longitude, searchFor, currentTime, peakHoursServingRadiusInKms));


      List<Restaurant> responseList =
          restaurantList.stream().distinct().collect(Collectors.toList());

      response.setRestaurants(responseList);
    } else {
      List<Restaurant> restaurantList = restaurantRepositoryService.findRestaurantsByName(latitude,
          longitude, searchFor, currentTime, normalHoursServingRadiusInKms);
      restaurantList.addAll(restaurantRepositoryService.findRestaurantsByAttributes(latitude,
          longitude, searchFor, currentTime, normalHoursServingRadiusInKms));
      restaurantList.addAll(restaurantRepositoryService.findRestaurantsByItemName(latitude,
          longitude, searchFor, currentTime, normalHoursServingRadiusInKms));
      restaurantList.addAll(restaurantRepositoryService.findRestaurantsByItemAttributes(latitude,
          longitude, searchFor, currentTime, normalHoursServingRadiusInKms));
      List<Restaurant> responseList =
          restaurantList.stream().distinct().collect(Collectors.toList());
      response.setRestaurants(responseList);
    }
    return response;
  }


}

