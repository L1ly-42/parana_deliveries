package com.example.demo.services;

import com.example.demo.models.*;
import com.example.demo.repositories.DeliveryRepository;
import com.example.demo.repositories.RouteRepository;
import com.example.demo.repositories.TruckRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.*;

@Service
public class RouteService {

    @Autowired
    RouteRepository routeRepository;

    @Autowired
    TruckRepository truckRepository;

    @Autowired
    DeliveryRepository deliveryRepository;

    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    public Optional<Route> getRouteById(long id){
        Optional<Route> route = routeRepository.findById(id);
        return route;
    }

    public Route updateRouteStatus(long id, StatusEnum newStatus){
        Optional<Route> targetRoute = getRouteById(id);

        if(targetRoute.isEmpty()){
            return null;
        }
        Route routeToUpdate = targetRoute.get();
        routeToUpdate.setStatus(newStatus);
        routeRepository.save(routeToUpdate);
        return routeToUpdate;
    }

    public Route createRoute(){
        // Get list of available trucks
        ArrayList<Truck> availableTrucks = truckRepository.findByAvailability(AvailabilityEnum.IN_DEPOT);
        // Check there is a truck available
        if(availableTrucks.isEmpty()){
            return null;
        }
        // create new route that will have this truck assigned to it and a status of ...PENDING
        Route newRoute = new Route(availableTrucks.get(0), StatusEnum.PENDING);
        availableTrucks.get(0).setAvailability(AvailabilityEnum.OUT_FOR_DELIVERY);
        routeRepository.save(newRoute);
        return newRoute;
    }

    public void deleteAllRoutes(){
        List<Delivery> allDeliveries = deliveryRepository.findAll();
        for(Delivery delivery : allDeliveries) {
            delivery.setRoute(null);
        }
        routeRepository.deleteAll();
        List<Truck> allTrucks = truckRepository.findAll();
        for(Truck truck : allTrucks) {
            truck.setAvailability(AvailabilityEnum.IN_DEPOT);
            truckRepository.save(truck);
        }
    }


    public List<ClusterDTO> kMeansClustering(int k, List<Delivery> deliveries) {
        // k = trucksActiveToday
        // 1. find extremes of values in locations to define boundary
        double latMin = 180, latMax = -180, lngMin = 180, lngMax = -180;
        for(int i=0; i < deliveries.size(); i++){
            latMin = Math.min(latMin, deliveries.get(i).getLocation().getLatitude());
            latMax = Math.max(latMax, deliveries.get(i).getLocation().getLatitude());
            lngMin = Math.min(lngMin, deliveries.get(i).getLocation().getLongitude());
            lngMax = Math.max(lngMax, deliveries.get(i).getLocation().getLongitude());
        }
        latMin += (latMax - latMin)/4;
        latMax -= (latMax - latMin)/4;
        lngMin += (lngMax - lngMin)/4;
        lngMax -= (lngMax - lngMin)/4;
        // 2. randomly assign k centroids within boundary
        ArrayList<ClusterDTO> clusters = new ArrayList<>();

        // dummy cluster to initially assign all deliveries to
        Double[] dummyCentroid = {-22.951916,-43.2130675};
        ClusterDTO dummyCluster = new ClusterDTO(dummyCentroid);
        clusters.add(dummyCluster);

        for(int i=0; i < k; i++){
            Random random = new Random();
            double randomLat = latMin + (random.nextDouble() * (latMax - latMin));
            double randomLng = lngMin + (random.nextDouble() * (lngMax - lngMin));
            Double[] newCentroid = {randomLat, randomLng};
            ClusterDTO newCluster = new ClusterDTO(newCentroid);
            clusters.add(newCluster);
        }

        int repeats = 0;
        boolean clustersConverged = false;
        Map<Delivery, ClusterDTO> clusterMapCurrent = new HashMap<>();
        Map<Delivery, ClusterDTO> clusterMapPrevious = new HashMap<>();
        for(Delivery delivery : deliveries){
            clusterMapCurrent.put(delivery, dummyCluster);
        }
        while(repeats < 50 && !clustersConverged) {
            // 3. assign each delivery to closest centroid
            for(ClusterDTO cluster : clusters) {
                cluster.setDeliveries(new ArrayList<>());
            }
            for(Delivery delivery : deliveries){
                int closestClusterIndex = clusters.indexOf(clusterMapCurrent.get(delivery));
                for(int i=0; i < clusters.size(); i++){

                    if(clusters.get(i).getDeliveries().size() == 11){continue;}
                    double distance = clusters.get(i).calculateDistance(delivery.getLocation());
                    if(distance < clusters.get(closestClusterIndex).calculateDistance(delivery.getLocation())){
                        closestClusterIndex = i;
                    }
                }
                clusters.get(closestClusterIndex).addDelivery(delivery);
                clusterMapPrevious = clusterMapCurrent;
                clusterMapCurrent.put(delivery, clusters.get(closestClusterIndex));
            }

            clustersConverged = clusterMapCurrent.equals(clusterMapPrevious);
            // remove dummy cluster after first iteration
            if(repeats == 0) {
                clusters.remove(0);
            }

            // 4. redefine centroids based on clusters
            for(ClusterDTO cluster : clusters){
                cluster.setCentroid(cluster.calculateAveragePoint());
            }
            // 5. repeat from step 3
            repeats++;
        }

        // max 10 repeats

        return clusters;
    }

    public List<Route> generateRoutes(){
        // how many trucks fo today? deliveriesToday/minDeliveries
        int maxTrucks = truckRepository.findByAvailability(AvailabilityEnum.IN_DEPOT).size();
        // minimum deliveries to send out a truck = 5
        List<Delivery> deliveriesToday = deliveryRepository.findByIsDelivered(false);
        int proposedTrucks = (int) Math.ceil(deliveriesToday.size()/5.0);
        int trucksActiveToday = Math.min(maxTrucks, proposedTrucks);
        // pass these trucks into k-means algorithm to assign deliveries
        List<ClusterDTO> clusters = kMeansClustering(trucksActiveToday, deliveriesToday);
        // make new route for each cluster and add deliveries
        List<Route> newRoutes = new ArrayList<>();
        for(ClusterDTO cluster : clusters){
            if(cluster.getDeliveries().isEmpty()){
                continue;
            }
            Route newRoute = createRoute();
            newRoute.setDeliveries(cluster.getDeliveries());
            newRoute.setStatus(StatusEnum.IN_PROGRESS);
            routeRepository.save(newRoute);
            newRoutes.add(newRoute);
            for(Delivery delivery : newRoute.getDeliveries()){
                delivery.setRoute(newRoute);
                deliveryRepository.save(delivery);
            }
        }
        return newRoutes;
    }

}
