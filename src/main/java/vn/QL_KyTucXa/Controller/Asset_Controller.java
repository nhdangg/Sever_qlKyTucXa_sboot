package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Asset_Request;
import vn.QL_KyTucXa.DTO.Response.Asset_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.Asset_Service;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Asset_Controller {

    Asset_Service assetService;

    @PostMapping
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public Api_Response<Asset_Response> createAsset(@RequestBody @Valid Asset_Request request) {
        return Api_Response.<Asset_Response>builder()
                .result(assetService.create(request))
                .build();
    }

    @GetMapping("/{assetId}")
    @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public Api_Response<Asset_Response> getAssetById(@PathVariable String assetId) {
        return Api_Response.<Asset_Response>builder()
                .result(assetService.getById(assetId))
                .build();
    }

    @GetMapping("/code/{assetCode}")
    @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public Api_Response<List<Asset_Response>> getAssetsByCode(@PathVariable String assetCode) {
        return Api_Response.<List<Asset_Response>>builder()
                .result(assetService.getByAssetCode(assetCode))
                .build();
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public Api_Response<List<Asset_Response>> getAssetsByCategory(@PathVariable String category) {
        return Api_Response.<List<Asset_Response>>builder()
                .result(assetService.getByCategory(category))
                .build();
    }

    @GetMapping("/name/{assetName}")
    @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public Api_Response<List<Asset_Response>> getAssetsByName(@PathVariable String assetName) {
        return Api_Response.<List<Asset_Response>>builder()
                .result(assetService.getByName(assetName))
                .build();
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public Api_Response<List<Asset_Response>> getAssetsByStatus(@PathVariable String status) {
        return Api_Response.<List<Asset_Response>>builder()
                .result(assetService.getByStatus(status))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public Api_Response<Page_Response<Asset_Response>> getAllAssets(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "assetCode") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir
    ) {
        return Api_Response.<Page_Response<Asset_Response>>builder()
                .result(assetService.getAll(page, size, sortBy, sortDir))
                .build();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ASSET_VIEW')")
    public Api_Response<List<Asset_Response>> getAllAssetsWithoutPaging() {
        return Api_Response.<List<Asset_Response>>builder()
                .result(assetService.getAll())
                .build();
    }

    @PutMapping("/{assetId}")
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public Api_Response<Asset_Response> updateAsset(
            @PathVariable String assetId,
            @RequestBody @Valid Asset_Request request
    ) {
        return Api_Response.<Asset_Response>builder()
                .result(assetService.update(assetId, request))
                .build();
    }

    @DeleteMapping("/{assetId}")
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public Api_Response<String> deleteAsset(@PathVariable String assetId) {
        assetService.delete(assetId);
        return Api_Response.<String>builder()
                .result(assetId)
                .build();
    }
}
