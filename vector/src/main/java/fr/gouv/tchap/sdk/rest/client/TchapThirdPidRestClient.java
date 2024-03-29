/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.gouv.tchap.sdk.rest.client;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.rest.DefaultRetrofit2ResponseHandler;
import org.matrix.androidsdk.rest.model.BulkLookupResponse;
import org.matrix.androidsdk.core.model.HttpError;
import org.matrix.androidsdk.core.model.HttpException;
import org.matrix.androidsdk.rest.model.pid.PidResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.gouv.tchap.sdk.rest.api.TchapThirdPidApi;
import fr.gouv.tchap.sdk.rest.model.TchapBulkLookupParams;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TchapThirdPidRestClient extends RestClient<TchapThirdPidApi> {

    /**
     * {@inheritDoc}
     */
    public TchapThirdPidRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, TchapThirdPidApi.class, URI_API_PREFIX_PATH_UNSTABLE, false, false);
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     *
     * @param address  3rd party id
     * @param medium   the media.
     * @param callback the 3rd party callback
     */
    public void lookup(String address,
                       String medium,
                       final ApiCallback<String> callback) {
        mApi.lookup(address, medium, mHsConfig.getIdentityServerUri().getHost()).enqueue(new Callback<PidResponse>() {
            @Override
            public void onResponse(Call<PidResponse> call, Response<PidResponse> response) {
                try {
                    handleLookupResponse(response, callback);
                } catch (IOException e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<PidResponse> call, Throwable t) {
                callback.onUnexpectedError((Exception) t);
            }
        });
    }

    private void handleLookupResponse(
            Response<PidResponse> response,
            final ApiCallback<String> callback
    ) throws IOException {
        DefaultRetrofit2ResponseHandler.handleResponse(
                response,
                new DefaultRetrofit2ResponseHandler.Listener<PidResponse>() {
                    @Override
                    public void onSuccess(Response<PidResponse> response) {
                        PidResponse pidResponse = response.body();
                        callback.onSuccess((null == pidResponse.mxid) ? "" : pidResponse.mxid);
                    }

                    @Override
                    public void onHttpError(HttpError httpError) {
                        callback.onNetworkError(new HttpException(httpError));
                    }
                }
        );
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     *
     * @param addresses 3rd party ids
     * @param mediums   the media.
     * @param callback  the 3rd parties callback
     */
    public void bulkLookup(final List<String> addresses,
                           final List<String> mediums,
                           final ApiCallback<List<String>> callback) {
        // sanity checks
        if ((null == addresses) || (null == mediums) || (addresses.size() != mediums.size())) {
            callback.onUnexpectedError(new Exception("invalid params"));
            return;
        }

        // nothing to check
        if (0 == mediums.size()) {
            callback.onSuccess(new ArrayList<String>());
            return;
        }

        TchapBulkLookupParams threePidsParams = new TchapBulkLookupParams();

        List<List<String>> list = new ArrayList<>();

        for (int i = 0; i < addresses.size(); i++) {
            list.add(Arrays.asList(mediums.get(i), addresses.get(i)));
        }

        threePidsParams.threepids = list;
        threePidsParams.idServer = mHsConfig.getIdentityServerUri().getHost();

        mApi.bulkLookup(threePidsParams).enqueue(new Callback<BulkLookupResponse>() {
            @Override
            public void onResponse(Call<BulkLookupResponse> call, Response<BulkLookupResponse> response) {
                try {
                    handleBulkLookupResponse(response, addresses, callback);
                } catch (IOException e) {
                    callback.onUnexpectedError(e);
                }
            }

            @Override
            public void onFailure(Call<BulkLookupResponse> call, Throwable t) {
                callback.onUnexpectedError((Exception) t);
            }
        });
    }

    private void handleBulkLookupResponse(
            Response<BulkLookupResponse> response,
            final List<String> addresses,
            final ApiCallback<List<String>> callback
    ) throws IOException {
        DefaultRetrofit2ResponseHandler.handleResponse(
                response,
                new DefaultRetrofit2ResponseHandler.Listener<BulkLookupResponse>() {
                    @Override
                    public void onSuccess(Response<BulkLookupResponse> response) {
                        handleBulkLookupSuccess(response, addresses, callback);
                    }

                    @Override
                    public void onHttpError(HttpError httpError) {
                        callback.onNetworkError(new HttpException(httpError));
                    }
                }
        );
    }

    private void handleBulkLookupSuccess(
            Response<BulkLookupResponse> response,
            List<String> addresses,
            ApiCallback<List<String>> callback
    ) {
        BulkLookupResponse bulkLookupResponse = response.body();
        Map<String, String> mxidByAddress = new HashMap<>();

        if (null != bulkLookupResponse.threepids) {
            for (int i = 0; i < bulkLookupResponse.threepids.size(); i++) {
                List<String> items = bulkLookupResponse.threepids.get(i);
                // [0] : medium
                // [1] : address
                // [2] : matrix id
                mxidByAddress.put(items.get(1), items.get(2));
            }
        }

        List<String> matrixIds = new ArrayList<>();

        for (String address : addresses) {
            if (mxidByAddress.containsKey(address)) {
                matrixIds.add(mxidByAddress.get(address));
            } else {
                matrixIds.add("");
            }
        }

        callback.onSuccess(matrixIds);
    }
}
