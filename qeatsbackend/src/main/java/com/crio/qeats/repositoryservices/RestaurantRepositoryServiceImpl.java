/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.MenuEntity;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.repositories.RestaurantRepository;
import com.crio.qeats.utils.GeoLocation;
import com.crio.qeats.utils.GeoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

@Primary
@Service
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {

  @Autowired
  private RedisConfiguration redisConfiguration;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private Provider<ModelMapper> modelMapperProvider;

  @Autowired 
  private RestaurantRepository restaurantRepository;

  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
  }

  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objectives:
  // 1. Implement findAllRestaurantsCloseby.
  // 2. Remember to keep the precision of GeoHash in mind while using it as a key.
  // Check RestaurantRepositoryService.java file for the interface contract.
  public List<Restaurant> findAllRestaurantsCloseBy(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {
    if (redisConfiguration.isCacheAvailable()) {
      return findAllRestaurantsCloseFromCache(latitude, longitude, currentTime, servingRadiusInKms);
    } else {
      return findAllRestaurantsCloseFromMongoDb(latitude, longitude, currentTime,
          servingRadiusInKms);
    }

   
  }





  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objective:
  // 1. Check if a restaurant is nearby and open. If so, it is a candidate to be returned.
  // NOTE: How far exactly is "nearby"?

  private List<Restaurant> findAllRestaurantsCloseFromMongoDb(Double latitude, Double longitude,
                                                         LocalTime currentTime,
                                                         Double servingRadiusInKms) {
    ModelMapper modelMapper = modelMapperProvider.get();
    ObjectMapper objectMapper = new ObjectMapper();

    List<RestaurantEntity> restaurantEntityList = restaurantRepository.findAll();

    List<Restaurant> restaurantList = new ArrayList<>();
    for (RestaurantEntity restaurantEntity : restaurantEntityList) {

      if (isOpenNow(currentTime, restaurantEntity)) {
        if (GeoUtils.findDistanceInKm(latitude, longitude,
                restaurantEntity.getLatitude(), restaurantEntity.getLongitude())
                < servingRadiusInKms) {
          restaurantList.add(modelMapper.map(restaurantEntity, Restaurant.class));
        }
      }
    }

    String restaurantDbString = "";
    redisConfiguration.initCache();
    try {
      restaurantDbString = objectMapper.writeValueAsString(restaurantList);
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.out.print(restaurantDbString);

    GeoLocation geoLocation = new GeoLocation(latitude,longitude);
    GeoHash geoHash = GeoHash.withCharacterPrecision(geoLocation.getLatitude(),
        geoLocation.getLongitude(),7);
    Jedis jedis = redisConfiguration.getJedisPool().getResource();
    jedis.set(geoHash.toBase32(),restaurantDbString);

    return restaurantList;
  }


  private List<Restaurant> findAllRestaurantsCloseFromCache(Double latitude, Double longitude,
      LocalTime currentTime, Double servingRadiusInKms) {
    System.out.println("######## in JEDIS Block ########");
    List<Restaurant> restaurants = new ArrayList<Restaurant>();
    GeoLocation geoLocation = new GeoLocation(latitude, longitude);
    GeoHash geoHash =
        GeoHash.withCharacterPrecision(geoLocation.getLatitude(), geoLocation.getLongitude(), 7);

    Jedis jedis = redisConfiguration.getJedisPool().getResource();

    if (!jedis.exists(geoHash.toBase32())) {
      return findAllRestaurantsCloseFromMongoDb(latitude, longitude, currentTime,
          servingRadiusInKms);
    }

    String restaurantString = "";
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      restaurantString = jedis.get(geoHash.toBase32());
      restaurants =
          objectMapper.readValue(restaurantString, new TypeReference<List<Restaurant>>() {});
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.out.print(restaurantString);

    return restaurants;
  }


  @Override
  public List<Restaurant> findRestaurantsByName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    ModelMapper modelMapper = modelMapperProvider.get();

    BasicQuery query = new BasicQuery("{name: {$regex: /" + searchString + "/i}}");
    List<RestaurantEntity> restaurantEntityList =
        mongoTemplate.find(query, RestaurantEntity.class, "restaurants");
    
    List<Restaurant> restaurantList = new ArrayList<>();
    for (RestaurantEntity restaurantEntity : restaurantEntityList) {

      if (isOpenNow(currentTime, restaurantEntity)) {
        if (GeoUtils.findDistanceInKm(latitude, longitude, restaurantEntity.getLatitude(),
            restaurantEntity.getLongitude()) < servingRadiusInKms) {
          restaurantList.add(modelMapper.map(restaurantEntity, Restaurant.class));
        }
      }
    }

    return restaurantList;

  }


  @Override
  public List<Restaurant> findRestaurantsByAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    ModelMapper modelMapper = modelMapperProvider.get();

    BasicQuery query = new BasicQuery("{attributes: {$regex: /" + searchString + "/i}}");
    List<RestaurantEntity> restaurantEntityList =
        mongoTemplate.find(query, RestaurantEntity.class, "restaurants");
    
    List<Restaurant> restaurantList = new ArrayList<>();
    for (RestaurantEntity restaurantEntity : restaurantEntityList) {

      if (isOpenNow(currentTime, restaurantEntity)) {
        if (GeoUtils.findDistanceInKm(latitude, longitude, restaurantEntity.getLatitude(),
            restaurantEntity.getLongitude()) < servingRadiusInKms) {
          restaurantList.add(modelMapper.map(restaurantEntity, Restaurant.class));
        }
      }
    }

    return restaurantList;

  }



  @Override
  public List<Restaurant> findRestaurantsByItemName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    ModelMapper modelMapper = modelMapperProvider.get();

    BasicQuery query = new BasicQuery("{'items.name': {$regex: /" + searchString + "/i}}");
    List<MenuEntity> menus = mongoTemplate.find(query, MenuEntity.class, "menus");
    List<RestaurantEntity> restaurants = new ArrayList<>();
    for (MenuEntity menu : menus) {
      String restaurantId = menu.getRestaurantId();
      BasicQuery restaurantQuery = new BasicQuery("{restaurantId:" + restaurantId + "}");
      restaurants
          .add(mongoTemplate.findOne(restaurantQuery, RestaurantEntity.class, "restaurants"));
    }

    List<Restaurant> restaurantList = new ArrayList<>();
    for (RestaurantEntity restaurantEntity : restaurants) {

      if (isOpenNow(currentTime, restaurantEntity)) {
        if (GeoUtils.findDistanceInKm(latitude, longitude, restaurantEntity.getLatitude(),
            restaurantEntity.getLongitude()) < servingRadiusInKms) {
          restaurantList.add(modelMapper.map(restaurantEntity, Restaurant.class));
        }
      }
    }

    return restaurantList;

  }


  
  @Override
  public List<Restaurant> findRestaurantsByItemAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    ModelMapper modelMapper = modelMapperProvider.get();

    BasicQuery query = new BasicQuery("{'items.attributes': {$regex: /" + searchString + "/i}}");
    List<MenuEntity> menus = mongoTemplate.find(query, MenuEntity.class, "menus");
    List<RestaurantEntity> restaurants = new ArrayList<>();
    for (MenuEntity menu : menus) {
      String restaurantId = menu.getRestaurantId();
      BasicQuery restaurantQuery = new BasicQuery("{restaurantId:" + restaurantId + "}");
      restaurants
          .add(mongoTemplate.findOne(restaurantQuery, RestaurantEntity.class, "restaurants"));
    }

    List<Restaurant> restaurantList = new ArrayList<>();
    for (RestaurantEntity restaurantEntity : restaurants) {

      if (isOpenNow(currentTime, restaurantEntity)) {
        if (GeoUtils.findDistanceInKm(latitude, longitude, restaurantEntity.getLatitude(),
            restaurantEntity.getLongitude()) < servingRadiusInKms) {
          restaurantList.add(modelMapper.map(restaurantEntity, Restaurant.class));
        }
      }
    }

    return restaurantList;

  }




  
}

