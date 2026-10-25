package com.tamdaptit.rentalmanagement.subscription.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.payment.entity.Payment;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentType;
import com.tamdaptit.rentalmanagement.payment.repository.PaymentRepository;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServiceSubscriptionOrderRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.*;
import com.tamdaptit.rentalmanagement.subscription.entity.*;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderType;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.SubscriptionStatus;
import com.tamdaptit.rentalmanagement.subscription.mapper.ServiceSubscriptionMapper;
import com.tamdaptit.rentalmanagement.subscription.repository.*;
import com.tamdaptit.rentalmanagement.subscription.service.ServiceSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

import static com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderStatus.*;
import static com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderType.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceSubscriptionServiceImpl implements ServiceSubscriptionService {
    private final UserRepository userRepository;
    private final ServicePackagePriceRepository priceRepository;
    private final ServicePackageSubscriptionRepository subscriptionRepository;
    private final ServiceSubscriptionOrderRepository orderRepository;
    private final ServiceSubscriptionPeriodRepository periodRepository;
    private final PaymentRepository paymentRepository;
    private final ListingRepository listingRepository;
    private final ServiceSubscriptionMapper mapper;
    private final Clock subscriptionClock;

    private LocalDateTime now() {
        return LocalDateTime.now(subscriptionClock).truncatedTo(ChronoUnit.SECONDS);
    }

    @Override
    @Transactional
    public void synchronizeOwner(String landlordId) {
        lockOwner(landlordId);
        synchronize(landlordId, now());
    }

    private User lockOwner(String landlordId) {
        return userRepository.findByIdForUpdate(landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
    }

    @Override
    @Transactional
    public ServiceSubscriptionOrderResponse createOrder(String landlordId, ServiceSubscriptionOrderRequest request) {
        User owner = lockOwner(landlordId);
        requireActive(owner);
        LocalDateTime time = now();
        var subscriptions = synchronize(landlordId, time);
        var active = active(subscriptions);
        var open = openOrders(landlordId);
        var price = priceRepository.findById(request.priceOptionId())
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PACKAGE_PRICE_NOT_FOUND));
        var catalog = price.getPackageEntity();
        if (!Boolean.TRUE.equals(price.getActive()) || !Boolean.TRUE.equals(catalog.getActive())
                || "FREE".equalsIgnoreCase(catalog.getCode())) {
            throw new AppException(ErrorCode.INVALID_SERVICE_PACKAGE);
        }
        ServiceSubscriptionOrderType type = request.type();
        boolean scheduled = open.stream().anyMatch(o -> o.getStatus() == SCHEDULED);
        if (scheduled || (type == PURCHASE && active != null)
                || (type == CHANGE && (active == null || samePackage(active, catalog)))
                || (type == CHANGE && open.stream().anyMatch(o -> o.getType() == CHANGE))
                || (type == RENEW && active != null && !samePackage(active, catalog))) {
            throw new AppException(ErrorCode.SUBSCRIPTION_ORDER_CONFLICT);
        }
        // An expired subscription is renewed by buying a new subscription, never by reviving its old periods.
        if (type == RENEW && active == null) {
            type = PURCHASE;
        }
        var order = ServiceSubscriptionOrder.builder().landlord(owner).packageEntity(catalog).priceOption(price)
                .type(type).durationMonths(price.getDurationMonths()).price(price.getPrice())
                .subscription(type == RENEW ? active : null)
                .previousSubscription(type == CHANGE ? active : null)
                .packageCodeSnapshot(catalog.getCode()).packageNameSnapshot(catalog.getName())
                .maxListingsSnapshot(catalog.getMaxListings()).highPrioritySlotsSnapshot(catalog.getHighPrioritySlots())
                .lowPrioritySlotsSnapshot(catalog.getLowPrioritySlots())
                .defaultPriorityLevelSnapshot(catalog.getDefaultPriorityLevel()).hasStatsSnapshot(catalog.getHasStats())
                .expiresAt(time.plusMinutes(15)).build();
        if (type == CHANGE) {
            var periods = periodRepository.findBySubscription_IdOrderByPeriodNumberAsc(active.getId());
            var currentPeriod = periods.stream().filter(p -> !time.isBefore(p.getStartAt()) && time.isBefore(p.getEndAt()))
                    .findFirst().orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR));
            if (isUpgrade(currentPeriod, catalog) && periods.stream().filter(p -> p.getEndAt().isAfter(time))
                    .allMatch(p -> java.util.Arrays.stream(compareRights(p, catalog)).allMatch(c -> c >= 0))) {
                BigDecimal credit = periods.stream().filter(p -> p.getEndAt().isAfter(time))
                        .map(p -> remainingPart(p, p.getEntitlementValue(), time)).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal months = periods.stream().filter(p -> p.getEndAt().isAfter(time))
                        .map(p -> remainingPart(p, p.getBillingMonths(), time)).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal targetValue = price.getPrice().multiply(months)
                        .divide(BigDecimal.valueOf(price.getDurationMonths()), 2, RoundingMode.HALF_UP);
                order.setImmediateUpgrade(true);
                order.setPreviousEndAt(active.getEndAt());
                order.setUpgradeValue(targetValue.max(credit).setScale(2, RoundingMode.HALF_UP));
                order.setUpgradeMonths(months);
                order.setPrice(targetValue.subtract(credit).max(BigDecimal.ZERO).setScale(0, RoundingMode.CEILING));
                if (order.getExpiresAt().isAfter(active.getEndAt())) order.setExpiresAt(active.getEndAt());
            }
        }
        orderRepository.saveAndFlush(order);
        if (Boolean.TRUE.equals(order.getImmediateUpgrade()) && order.getPrice().signum() == 0) {
            applyImmediateUpgrade(order, active, time);
        }
        return mapper.toResponse(order);
    }

    @Override
    @Transactional
    public CurrentSubscriptionResponse getCurrent(String landlordId) {
        requireActive(lockOwner(landlordId));
        LocalDateTime time = now();
        var subscriptions = synchronize(landlordId, time);
        var active = active(subscriptions);
        var scheduled = openOrders(landlordId).stream().filter(o -> o.getStatus() == SCHEDULED)
                .findFirst().map(mapper::toResponse).orElse(null);
        if (active != null) {
            var periods = periodRepository.findBySubscription_IdOrderByPeriodNumberAsc(active.getId());
            var period = periods.stream()
                    .filter(p -> !time.isBefore(p.getStartAt()) && time.isBefore(p.getEndAt()))
                    .findFirst().orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR));
            var nextPeriod = periods.stream().filter(p -> p.getStartAt().isAfter(time))
                    .findFirst().map(mapper::toResponse).orElse(null);
            return new CurrentSubscriptionResponse(active.getId(), false, true, active.getEndAt(),
                    mapper.toResponse(period), nextPeriod, mapper.toRights(period), scheduled);
        }
        boolean free = subscriptions.stream().noneMatch(s -> s.getStatus() != SubscriptionStatus.CANCELLED);
        var rights = free ? new SubscriptionRightsResponse("FREE", "Free", 3, 0, 0, 0, false)
                : new SubscriptionRightsResponse(null, null, 0, 0, 0, 0, false);
        return new CurrentSubscriptionResponse(null, free, free, null, null, null, rights, scheduled);
    }

    @Override
    @Transactional
    public ServiceSubscriptionOrderResponse applyPaidOrder(String landlordId, String orderId) {
        User owner = lockOwner(landlordId);
        LocalDateTime time = now();
        var subscriptions = synchronize(landlordId, time);
        var order = findOrder(landlordId, orderId);
        if (order.getStatus() == APPLIED && Boolean.TRUE.equals(order.getImmediateUpgrade()) && order.getPrice().signum() == 0) {
            return mapper.toResponse(order);
        }
        requirePaid(order);
        if (order.getStatus() == APPLIED || order.getStatus() == SCHEDULED
                || order.getStatus() == AWAITING_ADMIN_CONFIRMATION || order.getStatus() == REQUIRES_REVIEW) {
            return mapper.toResponse(order);
        }
        if (owner.getStatus() != UserStatus.ACTIVE
                || (order.getStatus() != PENDING_PAYMENT && order.getStatus() != EXPIRED)
                || !paidInTime(order)) {
            return review(order);
        }
        var current = active(subscriptions);
        if (order.getType() == CHANGE) {
            // The old subscription may have expired while payment/admin confirmation was pending.
            if ((current != null && !current.getId().equals(order.getPreviousSubscription().getId()))
                    || openOrders(landlordId).stream().anyMatch(o -> o.getType() == CHANGE && !o.getId().equals(orderId))) {
                return review(order);
            }
            if (Boolean.TRUE.equals(order.getImmediateUpgrade())) {
                if (current == null || !current.getEndAt().equals(order.getPreviousEndAt())
                        || openOrders(landlordId).stream().anyMatch(o -> o.getStatus() == SCHEDULED)) {
                    return review(order);
                }
                applyImmediateUpgrade(order, current, time);
            } else {
                LocalDateTime start = current == null ? time : current.getEndAt();
                appendPeriod(order, null, start, time, start.isAfter(time));
            }
        } else {
            if (openOrders(landlordId).stream().anyMatch(o -> o.getStatus() == SCHEDULED)) {
                return review(order);
            }
            if (order.getType() == PURCHASE && current != null) {
                return review(order);
            }
            if (order.getType() == RENEW && current != null
                    && !current.getId().equals(order.getSubscription().getId())) {
                return review(order);
            }
            LocalDateTime start = current == null ? time : current.getEndAt();
            appendPeriod(order, current, start, time, false);
        }
        return mapper.toResponse(orderRepository.saveAndFlush(order));
    }

    @Override
    @Transactional
    public ServiceSubscriptionOrderResponse confirmChange(String adminId, String orderId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        requireActive(admin);
        String landlordId = orderRepository.findLandlordIdById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.SUBSCRIPTION_ORDER_NOT_FOUND));
        User owner = lockOwner(landlordId);
        LocalDateTime time = now();
        var subscriptions = synchronize(landlordId, time);
        var order = findOrder(landlordId, orderId);
        requirePaid(order);
        if (order.getType() != CHANGE) {
            throw new AppException(ErrorCode.SUBSCRIPTION_ORDER_NOT_CONFIRMABLE);
        }
        if (order.getStatus() == SCHEDULED || order.getStatus() == APPLIED) {
            return mapper.toResponse(order);
        }
        if (order.getStatus() != AWAITING_ADMIN_CONFIRMATION) {
            throw new AppException(ErrorCode.SUBSCRIPTION_ORDER_NOT_CONFIRMABLE);
        }
        var current = active(subscriptions);
        if (owner.getStatus() != UserStatus.ACTIVE
                || (current != null && !current.getId().equals(order.getPreviousSubscription().getId()))) {
            return review(order);
        }
        var oldEnd = order.getPreviousSubscription().getEndAt();
        var start = oldEnd.isAfter(time) ? oldEnd : time;
        order.setPreviousEndAt(oldEnd);
        order.setConfirmedBy(admin);
        order.setConfirmedAt(time);
        appendPeriod(order, null, start, time, start.isAfter(time));
        return mapper.toResponse(orderRepository.saveAndFlush(order));
    }

    private void appendPeriod(ServiceSubscriptionOrder order, ServicePackageSubscription subscription,
            LocalDateTime start, LocalDateTime time, boolean scheduled) {
        LocalDateTime end = Boolean.TRUE.equals(order.getImmediateUpgrade())
                ? order.getPreviousEndAt() : start.plusMonths(order.getDurationMonths());
        int number = 1;
        if (subscription == null) {
            subscription = subscriptionRepository.saveAndFlush(ServicePackageSubscription.builder()
                    .landlord(order.getLandlord()).packageEntity(order.getPackageEntity())
                    .startAt(start).endAt(end)
                    .status(scheduled ? SubscriptionStatus.SCHEDULED : SubscriptionStatus.ACTIVE).build());
        } else {
            number = periodRepository.findBySubscription_IdOrderByPeriodNumberAsc(subscription.getId()).stream()
                    .mapToInt(ServiceSubscriptionPeriod::getPeriodNumber).max().orElse(0) + 1;
            order.setPreviousEndAt(subscription.getEndAt());
            subscription.setEndAt(end);
            subscriptionRepository.saveAndFlush(subscription);
        }
        order.setSubscription(subscription);
        order.setEffectiveAt(start);
        order.setEndAt(end);
        order.setStatus(scheduled ? SCHEDULED : APPLIED);
        order.setAppliedAt(scheduled ? null : time);
        orderRepository.saveAndFlush(order);
        periodRepository.saveAndFlush(ServiceSubscriptionPeriod.builder()
                .subscription(subscription).order(order).periodNumber(number).startAt(start).endAt(end)
                .entitlementValue(Boolean.TRUE.equals(order.getImmediateUpgrade()) ? order.getUpgradeValue() : order.getPrice())
                .billingMonths(Boolean.TRUE.equals(order.getImmediateUpgrade()) ? order.getUpgradeMonths() : BigDecimal.valueOf(order.getDurationMonths()))
                .packageCodeSnapshot(order.getPackageCodeSnapshot()).packageNameSnapshot(order.getPackageNameSnapshot())
                .maxListingsSnapshot(order.getMaxListingsSnapshot()).highPrioritySlotsSnapshot(order.getHighPrioritySlotsSnapshot())
                .lowPrioritySlotsSnapshot(order.getLowPrioritySlotsSnapshot())
                .defaultPriorityLevelSnapshot(order.getDefaultPriorityLevelSnapshot()).hasStatsSnapshot(order.getHasStatsSnapshot())
                .build());
    }

    private BigDecimal remainingPart(ServiceSubscriptionPeriod period, BigDecimal value, LocalDateTime time) {
        LocalDateTime start = time.isAfter(period.getStartAt()) ? time : period.getStartAt();
        return value.multiply(BigDecimal.valueOf(ChronoUnit.SECONDS.between(start, period.getEndAt())))
                .divide(BigDecimal.valueOf(ChronoUnit.SECONDS.between(period.getStartAt(), period.getEndAt())),
                        12, RoundingMode.HALF_UP);
    }

    // A higher price alone is not an upgrade: none of the purchased rights may decrease.
    private boolean isUpgrade(ServiceSubscriptionPeriod current, ServicePackage target) {
        int[] comparison = compareRights(current, target);
        return java.util.Arrays.stream(comparison).allMatch(c -> c >= 0)
                && java.util.Arrays.stream(comparison).anyMatch(c -> c > 0);
    }

    private int[] compareRights(ServiceSubscriptionPeriod current, ServicePackage target) {
        long oldMax = current.getMaxListingsSnapshot() == null ? Long.MAX_VALUE : current.getMaxListingsSnapshot();
        long newMax = target.getMaxListings() == null ? Long.MAX_VALUE : target.getMaxListings();
        long oldLow = current.getDefaultPriorityLevelSnapshot() == 1 ? Long.MAX_VALUE : current.getLowPrioritySlotsSnapshot();
        long newLow = target.getDefaultPriorityLevel() == 1 ? Long.MAX_VALUE : target.getLowPrioritySlots();
        int high = Integer.compare(target.getHighPrioritySlots(), current.getHighPrioritySlotsSnapshot());
        int stats = Boolean.compare(target.getHasStats(), current.getHasStatsSnapshot());
        return new int[] {Long.compare(newMax, oldMax), Long.compare(newLow, oldLow), high, stats};
    }

    private void applyImmediateUpgrade(ServiceSubscriptionOrder order, ServicePackageSubscription current, LocalDateTime time) {
        var oldPeriods = periodRepository.findBySubscription_IdOrderByPeriodNumberAsc(current.getId());
        var oldPeriod = oldPeriods.stream().filter(p -> !time.isBefore(p.getStartAt()) && time.isBefore(p.getEndAt()))
                .findFirst().orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR));
        // Keep original periods and financial history. The replaced subscription no longer grants rights.
        current.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.saveAndFlush(current);
        appendPeriod(order, null, time, time, false);
        var newPeriod = periodRepository.findBySubscription_IdOrderByPeriodNumberAsc(order.getSubscription().getId()).getFirst();
        for (var listing : listingRepository.findByRoom_Landlord_Id(order.getLandlord().getId())) {
            if (listing.getServiceSubscriptionPeriod() != null
                    && listing.getServiceSubscriptionPeriod().getId().equals(oldPeriod.getId())) {
                if (listing.getPackagePriorityLevel() == 1 && order.getDefaultPriorityLevelSnapshot() == 1) {
                    listing.setPackagePriorityLevel(0);
                    listing.setServiceSubscriptionPeriod(null);
                } else {
                    listing.setServiceSubscriptionPeriod(newPeriod);
                }
            }
        }
        listingRepository.flush();
    }

    // Every mutation locks the landlord first. Current reads also reconcile due transitions under this lock.
    private List<ServicePackageSubscription> synchronize(String landlordId, LocalDateTime time) {
        var subscriptions = subscriptionRepository.findByLandlord_IdOrderByStartAtAsc(landlordId);
        for (var subscription : subscriptions) {
            if (subscription.getStatus() == SubscriptionStatus.ACTIVE && !time.isBefore(subscription.getEndAt())) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
            }
        }
        // Release the unique ACTIVE key before activating a scheduled subscription.
        subscriptionRepository.flush();
        var open = openOrders(landlordId);
        for (var order : open) {
            if (order.getStatus() == SCHEDULED && !time.isBefore(order.getEffectiveAt())) {
                var subscription = order.getSubscription();
                subscription.setStatus(time.isBefore(subscription.getEndAt())
                        ? SubscriptionStatus.ACTIVE : SubscriptionStatus.EXPIRED);
                order.setStatus(APPLIED);
                order.setAppliedAt(time);
            } else if (order.getStatus() == PENDING_PAYMENT && !time.isBefore(order.getExpiresAt())
                    && successfulPayments(order).isEmpty()) {
                order.setStatus(EXPIRED);
            }
        }
        orderRepository.flush();
        return subscriptions;
    }

    private List<ServiceSubscriptionOrder> openOrders(String landlordId) {
        return orderRepository.findByLandlord_IdAndStatusIn(landlordId,
                List.of(PENDING_PAYMENT, AWAITING_ADMIN_CONFIRMATION, SCHEDULED));
    }

    private ServicePackageSubscription active(List<ServicePackageSubscription> subscriptions) {
        return subscriptions.stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).findFirst().orElse(null);
    }

    private boolean samePackage(ServicePackageSubscription subscription, ServicePackage catalog) {
        return subscription.getPackageEntity().getId().equals(catalog.getId());
    }

    private ServiceSubscriptionOrder findOrder(String landlordId, String orderId) {
        return orderRepository.findByIdAndLandlord_Id(orderId, landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.SUBSCRIPTION_ORDER_NOT_FOUND));
    }

    private List<Payment> successfulPayments(ServiceSubscriptionOrder order) {
        return paymentRepository.findByServiceSubscriptionOrder_IdAndStatus(order.getId(), PaymentStatus.SUCCESS).stream()
                .filter(payment -> !Boolean.TRUE.equals(payment.getRequiresReview())).toList();
    }

    private void requirePaid(ServiceSubscriptionOrder order) {
        var payments = successfulPayments(order);
        if (payments.size() != 1 || payments.stream().anyMatch(p -> p.getType() != PaymentType.SERVICE_PACKAGE
                || !p.getLandlord().getId().equals(order.getLandlord().getId())
                || p.getAmount().compareTo(order.getPrice()) != 0 || p.getPaidAt() == null)) {
            throw new AppException(ErrorCode.SUBSCRIPTION_ORDER_NOT_PAID);
        }
    }

    private boolean paidInTime(ServiceSubscriptionOrder order) {
        var payment = successfulPayments(order).getFirst();
        return payment.getPaidAt().isBefore(order.getExpiresAt())
                && payment.getPaidAt().isBefore(payment.getExpiresAt());
    }

    private ServiceSubscriptionOrderResponse review(ServiceSubscriptionOrder order) {
        order.setStatus(REQUIRES_REVIEW);
        return mapper.toResponse(orderRepository.saveAndFlush(order));
    }

    @Override
    @Transactional
    public PageResponse<ServiceSubscriptionResponse> getHistory(String landlordId, int page, int size) {
        requireActive(lockOwner(landlordId));
        synchronize(landlordId, now());
        return page(subscriptionRepository.findByLandlord_Id(landlordId, pageable(page, size, "startAt")), mapper::toResponse);
    }

    @Override
    @Transactional
    public PageResponse<ServiceSubscriptionOrderResponse> getOrders(String landlordId, int page, int size) {
        requireActive(lockOwner(landlordId));
        synchronize(landlordId, now());
        return page(orderRepository.findByLandlord_Id(landlordId, pageable(page, size, "createdAt")), mapper::toResponse);
    }

    @Override
    @Transactional
    public ServiceSubscriptionOrderResponse getOrder(String landlordId, String orderId) {
        requireActive(lockOwner(landlordId));
        synchronize(landlordId, now());
        return mapper.toResponse(findOrder(landlordId, orderId));
    }

    @Override
    public PageResponse<ServiceSubscriptionPeriodResponse> getPeriods(String landlordId, int page, int size) {
        requireActive(userRepository.findById(landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));
        return page(periodRepository.findBySubscription_Landlord_Id(landlordId, pageable(page, size, "startAt")), mapper::toResponse);
    }

    @Override
    public PageResponse<ServiceSubscriptionOrderResponse> getAwaitingConfirmation(int page, int size) {
        return page(orderRepository.findByStatus(AWAITING_ADMIN_CONFIRMATION, pageable(page, size, "createdAt")), mapper::toResponse);
    }

    private PageRequest pageable(int page, int size, String field) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, field, "id"));
    }

    private <T, R> PageResponse<R> page(Page<T> result, Function<T, R> mapping) {
        return PageResponse.<R>builder().currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(mapping).toList()).build();
    }
}
