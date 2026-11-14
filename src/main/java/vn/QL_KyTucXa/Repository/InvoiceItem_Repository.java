package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.InvoiceItem;

import java.util.List;

@Repository
public interface InvoiceItem_Repository extends JpaRepository<InvoiceItem, String> {
    List<InvoiceItem> findByInvoice_InvoiceId(String invoiceId);
    boolean existsByItemTypeAndReferenceId(String itemType, String referenceId);
    List<InvoiceItem> findByItemTypeAndReferenceId(String itemType, String referenceId);
}