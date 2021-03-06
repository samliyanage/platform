/*
 * Copyright (c) 2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.registry.rest.api;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.registry.core.Comment;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.rest.api.model.CommentModel;
import org.wso2.carbon.registry.rest.api.security.RestAPIAuthContext;
import org.wso2.carbon.registry.rest.api.security.RestAPISecurityConstants;
import org.wso2.carbon.registry.rest.api.security.RestAPISecurityUtils;
import org.wso2.carbon.registry.rest.api.security.UnAuthorizedException;

/**
 * This class does the CRUD operations on the given resource.
 */
@Path("/comment")
public class SingleComment extends RestSuper {

	private Log log = LogFactory.getLog(SingleComment.class);

	/**
	 * This method get a specific comment of the given resource
	 * 
	 * @param resourcePath
	 *            - registry path of the resource
	 * @param commentID
	 *            - comment id
	 * @param username
	 *            - enduser's username
	 * @param tenantID
	 *            - enduser's tenantID
	 * @return CommentModel JSON object
	 */
	@GET
	@Produces("application/json")
	public Response getACommentOnAResource(@QueryParam("path") String resourcePath,
	                                       @QueryParam("id") long commentID,
	                                       @HeaderParam("X-JWT-Assertion") String JWTToken) {
		RestAPIAuthContext authContext = RestAPISecurityUtils.isAuthorized
				(PrivilegedCarbonContext.getThreadLocalCarbonContext(), JWTToken);
		
		if (authContext.isAuthorized()) {
			String username = authContext.getUserName();
			int tenantID = authContext.getTenantId();
			super.createUserRegistry(username, tenantID);
			
			// null check for resource path
			if (RestPathPaginationValidation.validate(resourcePath) == -1) {
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			// null check for user registry instance
			if (super.getUserRegistry() == null) {
				throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
			}
			boolean exist;
			try {
				// check whether requested resource exists
				exist = super.getUserRegistry().resourceExists(resourcePath);
				if (exist) {
					// get all the comments on a resource
					Comment[] result = super.getUserRegistry().getComments(resourcePath);
					String commentPath = resourcePath + ";comments:" + commentID;
					CommentModel message = null;
					int size = result.length;
					for (int i = size - 1; i >= 0; i--) {
						String path = result[i].getCommentPath();
						if (path.equals(commentPath)) {
							message = new CommentModel(result[i]);
							break;
						}
					}
					// if comment can not be found for the specified comment ID
					if (message == null) {
						log.debug("The specific comment does not exist with the system");
						return Response.status(Response.Status.NOT_FOUND).build();
					} else {
						// returns the specified comment
						return Response.ok(message).build();
					}
				} else {
					log.debug("resource doesn't exist");
					// if requested resource not found
					return Response.status(Response.Status.NOT_FOUND).build();
				}
			} catch (RegistryException e) {
				log.error("user is not allowed to get a specific comment on a resource", e);
				throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
			}
		} else {
			throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
		}
	}

	/**
	 * 
	 * @param resourcePath
	 *            - resource path
	 * @param commentID
	 *            - id of the specific comment
	 * @param commentText
	 *            - comment to be added
	 * @param username
	 *            - end user's username
	 * @param tenantID
	 *            - enduser's tenantID.
	 * @return CommentModel object
	 */
	@PUT
	@Produces("application/json")
	public Response editACommentOnAResource(@QueryParam("path") String resourcePath,
	                                        @QueryParam("id") long commentID, String commentText,
	                                        @HeaderParam("X-JWT-Assertion") String JWTToken) {
		RestAPIAuthContext authContext = RestAPISecurityUtils.isAuthorized
				(PrivilegedCarbonContext.getThreadLocalCarbonContext(), JWTToken);
		
		if (authContext.isAuthorized()) {
			String username = authContext.getUserName();
			int tenantID = authContext.getTenantId();
			super.createUserRegistry(username, tenantID);
			
			if (RestPathPaginationValidation.validate(resourcePath) == -1) {
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			if (super.getUserRegistry() == null) {
				throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
			}
			boolean exist;
			try {
				// check for the existence of the resource
				exist = super.getUserRegistry().resourceExists(resourcePath);
				if (exist) {
					// get the comment path
					String commentPath = resourcePath + ";comments:" + commentID;
					super.getUserRegistry().editComment(commentPath, commentText);
					// get all the comments after updated
					Comment[] result = super.getUserRegistry().getComments(resourcePath);
					CommentModel message = null;
					int size = result.length;
					for (int i = size - 1; i >= 0; i--) {
						if (result[i].getCommentPath().equals(commentPath)) {
							message = new CommentModel(result[i]);
							break;
						}
					}
					// if no comments exist for the given resource
					if (message == null) {
						log.debug("The specific comment doesn't exist");
						return Response.status(Response.Status.NO_CONTENT).build();
					} else {
						// returns all the comments if exist
						return Response.ok(message).build();
					}
				} else {
					log.debug("Resource does not exist in the registry space");
					// if resource is not found
					return Response.status(Response.Status.NOT_FOUND).build();
				}
			} catch (RegistryException e) {
				log.error("user is not allowed to update the existing comment on a resource", e);
				throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
			}
		} else {
			throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
		}
	}

	/**
	 * This method deletes the specific comment on the given resource
	 * 
	 * @param resourcePath
	 *            - registry path of the resource
	 * @param commentID
	 *            - ID of the comment to be deleted
	 * @param username
	 *            - enduser's username
	 * @param tenantID
	 *            - tenantID of the enduser.
	 * @return HTTP status.
	 */
	@DELETE
	@Produces("application/json")
	public Response deleteACommentOnAResource(@QueryParam("path") String resourcePath,
	                                          @QueryParam("id") long commentID,
	                                          @HeaderParam("X-JWT-Assertion") String JWTToken) {
		RestAPIAuthContext authContext = RestAPISecurityUtils.isAuthorized
				(PrivilegedCarbonContext.getThreadLocalCarbonContext(), JWTToken);
		
		if (authContext.isAuthorized()) {
			String username = authContext.getUserName();
			int tenantID = authContext.getTenantId();
			super.createUserRegistry(username, tenantID);
			
			if (RestPathPaginationValidation.validate(resourcePath) == -1) {
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			if (super.getUserRegistry() == null) {
				throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
			}
			boolean exist;
			try {
				// check if resource exist
				exist = super.getUserRegistry().resourceExists(resourcePath);
				if (exist) {
					String commentPath = resourcePath + ";comments:" + commentID;
					// remove specified comment from the registry
					super.getUserRegistry().removeComment(commentPath);
					return Response.status(Response.Status.NO_CONTENT).build();
				} else {
					// if resource is not found
					return Response.status(Response.Status.NOT_FOUND).build();
				}
			} catch (RegistryException e) {
				log.error("user is not allowed to delete the specified comment on a resource", e);
				throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
			}
		} else {
			throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
		}
	}

	/**
	 * This method add the comment sent as payload to the given resource
	 * 
	 * @param resourcePath
	 *            - resource path
	 * @param commentText
	 *            - comment text
	 * @param username
	 *            - enduser's username
	 * @param tenantID
	 *            - enduser's tenantID
	 * @return HTTP 201 - if added
	 */
	@POST
	@Produces("application/json")
	public Response addCommentsOnAResource(@QueryParam("path") String resourcePath,
	                                       String commentText, @HeaderParam("X-JWT-Assertion") String JWTToken) {
		RestAPIAuthContext authContext = RestAPISecurityUtils.isAuthorized
				(PrivilegedCarbonContext.getThreadLocalCarbonContext(), JWTToken);
		
		if (authContext.isAuthorized()) {
			String username = authContext.getUserName();
			int tenantID = authContext.getTenantId();
			super.createUserRegistry(username, tenantID);
			
			if (RestPathPaginationValidation.validate(resourcePath) == -1) {
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			if (super.getUserRegistry() == null) {
				throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
			}
			boolean exist;
			try {
				exist = super.getUserRegistry().resourceExists(resourcePath);
				if (exist) {
					// if pass a null comment returns bad request
					if (commentText.length() == 0) {
						return Response.status(Response.Status.BAD_REQUEST).build();
					} else {
						// add a comment
						Comment comment = new Comment(commentText);
						super.getUserRegistry().addComment(resourcePath, comment);
						return Response.status(Response.Status.CREATED).build();
					}
				} else {
					// if resource is not found
					return Response.status(Response.Status.NOT_FOUND).build();
				}
			} catch (RegistryException e) {
				log.error("user is not allowed to add comment to a resource", e);
				throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
			}
		} else {
			throw new UnAuthorizedException(RestAPISecurityConstants.UNAUTHORIZED_ERROR);
		}
	}
}
