package com.realtime.monitor.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void testSuccessResponseWithData() {
        String testData = "Test Data";
        ApiResponse<String> response = ApiResponse.success(testData);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertNull(response.getMessage());
        assertNull(response.getError());
    }

    @Test
    void testSuccessResponseWithDataAndMessage() {
        String testData = "Test Data";
        String message = "Operation successful";
        ApiResponse<String> response = ApiResponse.success(testData, message);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertEquals(message, response.getMessage());
        assertNull(response.getError());
    }

    @Test
    void testErrorResponse() {
        String errorMessage = "Something went wrong";
        ApiResponse<Object> response = ApiResponse.error(errorMessage);

        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getError());
        assertNull(response.getData());
        assertNull(response.getMessage());
    }

    @Test
    void testBuilderAndSetters() {
        ApiResponse<Integer> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(42);
        response.setMessage("Found");
        response.setError(null);

        assertTrue(response.isSuccess());
        assertEquals(42, response.getData());
        assertEquals("Found", response.getMessage());
        assertNull(response.getError());

        ApiResponse<Double> buildResponse = ApiResponse.<Double>builder()
                .success(false)
                .error("Not Found")
                .build();

        assertFalse(buildResponse.isSuccess());
        assertEquals("Not Found", buildResponse.getError());
    }
}
