package com.ravita.organization.api;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.time.OffsetDateTime;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(ResourceNotFoundException.class) ResponseEntity<ApiError> notFound(ResourceNotFoundException e){return ResponseEntity.status(404).body(new ApiError("RESOURCE_NOT_FOUND",e.getMessage()));}
 @ExceptionHandler(DuplicateDocumentException.class) ResponseEntity<ApiError> duplicate(DuplicateDocumentException e){return ResponseEntity.status(409).body(new ApiError("DOCUMENT_ALREADY_REGISTERED",e.getMessage()));}
 @ExceptionHandler(InvalidOrganizationException.class) ResponseEntity<ApiError> invalid(InvalidOrganizationException e){return ResponseEntity.status(422).body(new ApiError("INVALID_ORGANIZATION",e.getMessage()));}
 public record ApiError(String code,String message,String timestamp){ApiError(String code,String message){this(code,message,OffsetDateTime.now().toString());}}
}
