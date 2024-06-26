package com.example.demo.services;

import com.example.demo.models.Delivery;
import com.example.demo.models.DeliveryDTO;
import com.example.demo.models.Location;
import com.example.demo.models.Route;
import com.example.demo.repositories.DeliveryRepository;
import com.example.demo.repositories.LocationRepository;
import com.example.demo.repositories.RouteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DeliveryService {

    @Autowired
    DeliveryRepository deliveryRepository;

    @Autowired
    LocationRepository locationRepository;

    public List<Delivery> getAllDeliveries(){
        return deliveryRepository.findAll();
    }

    public Optional<Delivery> getDeliveryById(long id){
        return deliveryRepository.findById(id);
    }

    public Delivery completeDelivery(long id){
        Delivery delivery = deliveryRepository.findById(id).get();
        delivery.setDelivered(!delivery.isDelivered());
        deliveryRepository.save(delivery);
        return delivery;
    }

    public Delivery saveDelivery(DeliveryDTO newDeliveryDTO){
        Location location = locationRepository.findById(newDeliveryDTO.getLocationId()).get();
        Delivery newDelivery = new Delivery(location);
        deliveryRepository.save(newDelivery);
        return newDelivery;
    }

    public Optional<Delivery> findDelivery(long id){
        Optional<Delivery> delivery = deliveryRepository.findById(id);
        return delivery;
    }
    public void deleteDelivery(long id){
        deliveryRepository.deleteById(id);
    }


}
