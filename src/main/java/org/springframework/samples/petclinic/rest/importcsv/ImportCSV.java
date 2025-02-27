package org.springframework.samples.petclinic.rest.importcsv;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        // Bad: We just put i = 10 here and this was not caught by any test
        // @todo: Open an issue? Write a new test?
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;

        String [] lines = csv.split("\n");

        for(String line: lines){
            String [] fields = line.split(";");
            // Process one line of the csv file
            pet = new Pet();

            boolean success = false;

            assert(fields.length >= 4);
            assert(fields.length <= 5);

            setName(pet, fields[0]);
            success = setBirthDate(pet, fields[1]);
            if(!success){
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "date " + fields[1] + " not valid");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }
            success = setType(pet, fields[2]);
            success = setOwner(pet, fields[3]);
            if(fields.length == 4){
                i = updateDatabase(pet, fields[4]);
            }
            pets.add(pet);
        }
        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    private int popNextField(int i, String field, String csv) {
        while (i < csv.length() && csv.charAt(i) != ';') {
            assert(csv.charAt(i) != '\n');
            field += csv.charAt(i++);
        }
        assert(csv.charAt(i) != '\n');
        assert(csv.charAt(i) == ';');
        i++;
        return i;
    }

    private int popNextFieldV2(int i, String field, String csv){
        while (i < csv.length() && (csv.charAt(i) != ';' && csv.charAt(i) != '\n')) {
            field += csv.charAt(i++);
        }
        return i;
    }

    private int popNextFieldV3(int i, String field, String csv){
        while (i < csv.length() && csv.charAt(i) != '\n') {
            assert(csv.charAt(i) != ';');
            field += csv.charAt(i++);
        }
        assert(csv.charAt(i) != ';');
        assert(csv.charAt(i) == '\n');
        return i;
    }

    private boolean areEqual(Pet onePet, Pet anotherPet) {
        return onePet.getName().equals(anotherPet.getName()) &&
            onePet.getType().getId().equals(anotherPet.getType().getId()) &&
            onePet.getBirthDate().equals(anotherPet.getBirthDate());
    }

    private int extractName(Pet pet, int i, String csv, String field) {
        i = popNextField(i, field, csv);
        i++;
        return i;
    }

    private boolean setBirthDate(Pet pet, String value){
        Date date;
        try {
            date = new SimpleDateFormat("yyyy-MM-dd").parse(value);
        } catch (ParseException e) {
            return false;
        }
        pet.setBirthDate(date);
        return true;
    }

    private boolean setType(Pet pet, String value){
        ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();
        for (int j = 0; j < ts.size(); j++) {
            if (ts.get(j).getName().toLowerCase().equals(value)) {
                pet.setType(ts.get(j));
                return true;
            }
        }
        return false;
    }

    private int extractOwner(Pet pet, int i, String csv) {
        String field = "";
        i = popNextFieldV2(i, field, csv);

        if (pet != null) {
            String owner = field;
            List<Owner> matchingOwners = clinicService.findAllOwners()
                .stream()
                .filter(o -> o.getLastName().equals(owner))
                .collect(Collectors.toList());

            if (matchingOwners.size() == 0) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "Owner not found");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }
            if (matchingOwners.size() > 1) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "Owner not unique");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }
            pet.setOwner(matchingOwners.iterator().next());
        }
        return i;
    }

    private int updateDatabase(Pet pet, int i, String csv) {
        if (csv.charAt(i) == ';') {
            i++;

            String field = "";
            i = popNextFieldV3(i, field, csv);

            if (field.toLowerCase().equals("add")) {
                clinicService.savePet(pet);
            } else if (field.toLowerCase().equals("delete")) {
                boolean petFound = false;
                for (Pet q : pet.getOwner().getPets()) {
                    if(areEqual(q, pet)){
                        clinicService.deletePet(q);
                        petFound = true;
                    }
                }
                assert(petFound);  // make sure that if a pet should be deleted it was also found. Otherwise: Invalid input.
            } else {
                assert(false);  // unexpected keyword
            }
        } else {
            clinicService.savePet(pet);
        }
        if(i < csv.length()) {
            assert(csv.charAt(i) != '\n');  // this is not the last line, but it must be terminated with a newline character
        }
        i++;
        return i;
    }
}
