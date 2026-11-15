package com.ravita.offer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Endereço de retirada. É uma cópia que pertence à oferta: se o doador mudar de endereço depois,
 * a oferta já criada não muda.
 */
@Embeddable
public class Address {

    @Column(name = "pickup_street", nullable = false, length = 150)
    private String street;

    @Column(name = "pickup_number", nullable = false, length = 20)
    private String number;

    @Column(name = "pickup_complement", length = 100)
    private String complement;

    @Column(name = "pickup_district", nullable = false, length = 100)
    private String district;

    @Column(name = "pickup_city", nullable = false, length = 100)
    private String city;

    @Column(name = "pickup_state", nullable = false, length = 2)
    private String state;

    @Column(name = "pickup_postal_code", nullable = false, length = 8)
    private String postalCode;

    protected Address() {
    }

    public Address(String street, String number, String complement, String district,
                   String city, String state, String postalCode) {
        this.street = street;
        this.number = number;
        this.complement = complement;
        this.district = district;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }

    public String getStreet() { return street; }
    public String getNumber() { return number; }
    public String getComplement() { return complement; }
    public String getDistrict() { return district; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
}
