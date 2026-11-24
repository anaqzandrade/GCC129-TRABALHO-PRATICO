package com.ravita.offer.api.dto;

import com.ravita.offer.domain.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Address do contrato (seção 1). Usado na entrada e na saída. */
public record AddressDto(
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 20) String number,
        @Size(max = 100) String complement,
        @NotBlank @Size(max = 100) String district,
        @NotBlank @Size(max = 100) String city,
        @NotNull @Pattern(regexp = "[A-Z]{2}", message = "duas letras maiúsculas") String state,
        @NotNull @Pattern(regexp = "\\d{8}", message = "oito dígitos") String postalCode) {

    public AddressDto {
        street = Text.trim(street);
        number = Text.trim(number);
        complement = Text.trimToNull(complement);
        district = Text.trim(district);
        city = Text.trim(city);
        state = Text.trim(state);
        postalCode = Text.trim(postalCode);
    }

    public Address toDomain() {
        return new Address(street, number, complement, district, city, state, postalCode);
    }

    public static AddressDto from(Address address) {
        return new AddressDto(address.getStreet(), address.getNumber(), address.getComplement(),
                address.getDistrict(), address.getCity(), address.getState(), address.getPostalCode());
    }
}
