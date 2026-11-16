package com.ravita.organization.domain;
import jakarta.persistence.Embeddable;
@Embeddable public class Address {
 private String street,number,district,city,state,postalCode;
 protected Address(){}
 public Address(String street,String number,String district,String city,String state,String postalCode){this.street=street;this.number=number;this.district=district;this.city=city;this.state=state;this.postalCode=postalCode;}
 public String getStreet(){return street;} public String getNumber(){return number;} public String getDistrict(){return district;} public String getCity(){return city;} public String getState(){return state;} public String getPostalCode(){return postalCode;}
}
