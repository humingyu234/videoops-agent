package org.dromara.aivideo.asset.service;

/** Boundary for private object storage. Internal keys never cross the HTTP boundary. */
public interface IObjectStorageService {

    SinglePutAuthorization createSinglePutAuthorization(String businessPrefix, String fileName, String contentType);

    SinglePutAuthorization createSinglePutAuthorizationForExistingObject(String objectKey, String contentType);

    record SinglePutAuthorization(String objectKey, String putUrl, String requiredContentType) {
    }
}
