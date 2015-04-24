/* * Copyright 2015 Google Inc. All Rights Reserved. * * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except * in compliance with the License. You may obtain a copy of the License at * * http://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, software distributed under the License * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express * or implied. See the License for the specific language governing permissions and limitations under * the License. */package com.google.gcloud.spi;import com.google.api.client.googleapis.json.GoogleJsonError;import com.google.api.client.googleapis.json.GoogleJsonResponseException;import com.google.api.client.http.AbstractInputStreamContent;import com.google.api.client.http.HttpRequestInitializer;import com.google.api.client.http.HttpTransport;import com.google.api.client.json.jackson.JacksonFactory;import com.google.api.services.storage.Storage;import com.google.api.services.storage.model.Bucket;import com.google.api.services.storage.model.StorageObject;import com.google.common.collect.ImmutableSet;import com.google.gcloud.storage.BlobReadChannel;import com.google.gcloud.storage.BlobWriteChannel;import com.google.gcloud.storage.StorageServiceException;import com.google.gcloud.storage.StorageServiceOptions;import java.io.ByteArrayInputStream;import java.io.ByteArrayOutputStream;import java.io.IOException;import java.io.InputStream;import java.util.Iterator;import java.util.Map;import java.util.Set;public class DefaultStorageRpc implements StorageRpc {  public static final String DEFAULT_PROJECTION = "full";  private final StorageServiceOptions options;  private final Storage storage;  private static final Set<Integer> RETRYABLE_CODES = ImmutableSet.of(500, 503);  public DefaultStorageRpc(StorageServiceOptions options) {    HttpTransport transport = options.httpTransportFactory().create();    HttpRequestInitializer initializer = options.httpRequestInitializer();    this.options = options;    storage = new Storage.Builder(transport, new JacksonFactory(), initializer).build();    // Todo: make sure nulls are being used as Data.asNull()    // TOdo: consider options  }  private StorageServiceException translate(IOException exception) {    StorageServiceException translated;    if (exception instanceof GoogleJsonResponseException) {      GoogleJsonError details = ((GoogleJsonResponseException) exception).getDetails();      boolean retryable = RETRYABLE_CODES.contains(details.getCode())          || "InternalError".equals(details.getMessage());      translated = new StorageServiceException(details.getCode(), details.getMessage(), retryable);    } else {      translated = new StorageServiceException(0, exception.getMessage(), false);    }    translated.initCause(exception);    return translated;  }  @Override  public Bucket create(Bucket bucket, Map<Option, ?> options) throws StorageServiceException {    try {      return storage.buckets()          .insert(this.options.project(), bucket)          .setProjection(DEFAULT_PROJECTION)          .execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public StorageObject create(StorageObject storageObject, final byte[] content,      Map<Option, ?> options) throws StorageServiceException {    try {      return storage.objects()          .insert(storageObject.getBucket(), storageObject,              new AbstractInputStreamContent(storageObject.getContentType()) {                @Override                public InputStream getInputStream() throws IOException {                  return new ByteArrayInputStream(content);                }                @Override                public long getLength() throws IOException {                  return content.length;                }                @Override                public boolean retrySupported() {                  return true;                }              })          .setProjection(DEFAULT_PROJECTION)          .execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public Iterator<Bucket> list() {    try {      return storage.buckets()          .list(options.project())          .setProjection(DEFAULT_PROJECTION)          .execute()          .getItems()          .iterator();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public Iterator<StorageObject> list(String bucket, String prefix, String delimiter,      String cursor, boolean includeOlderVersions, int limit) {    // todo: implement    return null;  }  @Override  public Bucket get(Bucket bucket, Map<Option, ?> options) {    try {      return storage.buckets().get(bucket.getName()).setProjection(DEFAULT_PROJECTION).execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public StorageObject get(StorageObject object, Map<Option, ?> options) {    try {      return storage.objects()          .get(object.getBucket(), object.getName())          .setProjection(DEFAULT_PROJECTION)          .execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public Bucket patch(Bucket bucket, Map<Option, ?> options) {    try {      return storage.buckets()          .patch(bucket.getName(), bucket).setProjection(DEFAULT_PROJECTION)          .execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public StorageObject patch(StorageObject storageObject, Map<Option, ?> options) {    try {      return storage.objects()          .patch(storageObject.getBucket(), storageObject.getName(), storageObject)          .setProjection(DEFAULT_PROJECTION)          .execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public void delete(Bucket bucket, Map<Option, ?> options) {    try {      storage.buckets().delete(bucket.getName()).execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public void delete(StorageObject blob, Map<Option, ?> options) {    try {      storage.objects().delete(blob.getBucket(), blob.getName()).execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public StorageObject compose(Iterable<StorageObject> sources, StorageObject target,      Map<Option, ?> targetOptions) throws StorageServiceException {    // todo: implement null -> ComposeRequest    // todo: missing setProjection    try {      return storage.objects()          .compose(target.getBucket(), target.getName(), null)          .execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public StorageObject copy(StorageObject source, Map<Option, ?> sourceOptions,      StorageObject target, Map<Option, ?> targetOptions) throws StorageServiceException {    try {      return storage          .objects()          .copy(source.getBucket(), source.getName(), target.getBucket(), target.getName(), target)          .setProjection(DEFAULT_PROJECTION)          .execute();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public byte[] load(StorageObject from, Map<Option, ?> options)      throws StorageServiceException {    try {      Storage.Objects.Get getRequest = storage.objects().get(from.getBucket(), from.getName());      if (from.getGeneration() != null && options.containsKey(Option.IF_GENERATION_MATCH)) {        if ((Boolean) options.get(Option.IF_GENERATION_MATCH)) {          getRequest.setIfGenerationMatch(from.getGeneration());        } else {          getRequest.setIfGenerationNotMatch(from.getGeneration());        }      }      if (from.getMetageneration() != null && options.containsKey(Option.IF_METAGENERATION_MATCH)) {        if ((Boolean) options.get(Option.IF_METAGENERATION_MATCH)) {          getRequest.setIfMetagenerationMatch(from.getMetageneration());        } else {          getRequest.setIfMetagenerationNotMatch(from.getMetageneration());        }      }      ByteArrayOutputStream out = new ByteArrayOutputStream();      getRequest.getMediaHttpDownloader().setDirectDownloadEnabled(true);      getRequest.executeMediaAndDownloadTo(out);      return out.toByteArray();    } catch (IOException ex) {      throw translate(ex);    }  }  @Override  public BlobReadChannel reader(StorageObject from, Map<Option, ?> options)      throws StorageServiceException {    // todo: implement    return null;  }  @Override  public BlobWriteChannel writer(StorageObject to, Map<Option, ?> options)      throws StorageServiceException {    // todo: implement    return null;  }}