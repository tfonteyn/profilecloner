/**
 *
 */
package org.jboss.tfonteyne.profilecloner.rest.mappers;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;

/**
 * @author Andrea Battaglia
 *
 */
@Provider
public class PCExceptionMapper implements ExceptionMapper<Exception> {

    @Inject
    private Logger LOG;

    /**
     * @see javax.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    @Override
    public Response toResponse(Exception e) {
        LOG.error("", e);
        return Response
                .status(Status.BAD_REQUEST)
                .entity("An exception occurred: \"" + e.getMessage()
                        + ".\" See the log file for error details.").build();
    }

}