package com.materialbook.reports;

enum MaterialType {
    IN("Material IN", "material-in", "Material IN Voucher"),
    OUT("Material OUT", "material-out", "Material OUT Challan");

    final String label;
    final String slug;
    final String documentTitle;

    MaterialType(String label, String slug, String documentTitle) {
        this.label = label;
        this.slug = slug;
        this.documentTitle = documentTitle;
    }
}
