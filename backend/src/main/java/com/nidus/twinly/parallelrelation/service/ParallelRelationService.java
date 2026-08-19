package com.nidus.twinly.parallelrelation.service;

import com.nidus.twinly.common.parallel.ParallelRelationType;
import com.nidus.twinly.common.parallel.ParallelRelationResolver;
import com.nidus.twinly.common.parallel.ParallelRelationResult;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.persona.PersonaSimilarity;
import com.nidus.twinly.common.persona.PersonaSimilarityCalculator;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.parallelrelation.dto.command.ParallelRelationSubmitCodeCommand;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationDetailResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationIssueCodeResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationListItemResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationListResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationSubmitCodeResult;
import com.nidus.twinly.parallelrelation.dto.result.ParallelRelationUserResult;
import com.nidus.twinly.parallelrelation.entity.ParallelRelationCode;
import com.nidus.twinly.parallelrelation.entity.ParallelRelation;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationCodeRepository;
import com.nidus.twinly.parallelrelation.repository.ParallelRelationRepository;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParallelRelationService {

    private static final String SHARE_MESSAGE_FORMAT = "[트윈리] 나랑 평행우주에서 무슨 사이인지 확인해보자! 코드: %s";
    private static final int SIMILARITY_PERCENT = 100;

    private final ParallelRelationCodeRepository parallelRelationCodeRepository;
    private final ParallelRelationRepository parallelRelationRepository;
    private final ParallelRelationCodeIssuer parallelRelationCodeIssuer;
    private final ParallelRelationResolver parallelRelationResolver;
    private final PersonaSimilarityCalculator personaSimilarityCalculator;
    private final PersonaElementRepository personaElementRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final CloudFrontService cloudFrontService;

    @Transactional
    public ParallelRelationIssueCodeResult issueCode(Long userId) {
        ParallelRelationCode parallelRelationCode = parallelRelationCodeRepository.findByUserId(userId)
                .orElseGet(() -> parallelRelationCodeRepository.save(ParallelRelationCode.create(userId, parallelRelationCodeIssuer.issue())));

        return new ParallelRelationIssueCodeResult(
                parallelRelationCode.getCode(),
                SHARE_MESSAGE_FORMAT.formatted(parallelRelationCode.getCode())
        );
    }

    @Transactional
    public ParallelRelationSubmitCodeResult submitCode(Long userId, ParallelRelationSubmitCodeCommand command) {
        Long codeOwnerId = parallelRelationCodeRepository.findByCode(command.code().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARALLEL_RELATION_CODE_NOT_FOUND))
                .getUserId();

        if (codeOwnerId.equals(userId)) {
            throw new BusinessException(ErrorCode.OWN_PARALLEL_RELATION_CODE);
        }

        Optional<ParallelRelation> existingRelation = parallelRelationRepository.findByUserAIdAndUserBId(
                Math.min(codeOwnerId, userId), Math.max(codeOwnerId, userId));
        if (existingRelation.isPresent()) {
            return new ParallelRelationSubmitCodeResult(false, toDetailResult(existingRelation.get(), userId));
        }

        checkNotWithdrawn(codeOwnerId);
        checkPersonaExists(codeOwnerId);
        checkPersonaExists(userId);

        PersonaSimilarity similarity = personaSimilarityCalculator.similarity(personaElements(codeOwnerId), personaElements(userId));
        ParallelRelationType relation = parallelRelationResolver.relationOf(similarity.score());

        ParallelRelation pair = parallelRelationRepository.save(ParallelRelation.create(
                codeOwnerId,
                userId,
                (int) Math.round(similarity.score() * SIMILARITY_PERCENT),
                relation,
                parallelRelationResolver.pickStoryIndex(relation)
        ));

        return new ParallelRelationSubmitCodeResult(true, toDetailResult(pair, userId));
    }

    public ParallelRelationListResult relationList(Long userId) {
        List<ParallelRelation> relations = parallelRelationRepository.findAllByUserAIdOrUserBIdOrderByIdDesc(userId, userId);
        if (relations.isEmpty()) {
            return new ParallelRelationListResult(List.of());
        }

        List<Long> partnerIds = relations.stream()
                .map(relation -> relation.partnerIdOf(userId))
                .toList();
        Map<Long, User> userById = userRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<Long> visiblePartnerIds = partnerIds.stream()
                .filter(partnerId -> !userById.get(partnerId).isWithdrawn())
                .toList();
        Map<Long, Photo> photoByUserId = photoRepository.findAllByUserIdInAndType(visiblePartnerIds, PhotoType.PROFILE).stream()
                .collect(Collectors.toMap(Photo::getUserId, Function.identity()));

        return new ParallelRelationListResult(relations.stream()
                .filter(relation -> !userById.get(relation.partnerIdOf(userId)).isWithdrawn())
                .map(relation -> toListItemResult(relation, userById.get(relation.partnerIdOf(userId)), photoByUserId))
                .toList());
    }

    public ParallelRelationDetailResult relationDetail(Long userId, Long parallelRelationId) {
        ParallelRelation relation = parallelRelationRepository.findById(parallelRelationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARALLEL_RELATION_NOT_FOUND));

        if (!relation.hasParticipant(userId)) {
            throw new BusinessException(ErrorCode.PARALLEL_RELATION_NOT_FOUND);
        }

        User partner = userRepository.findById(relation.partnerIdOf(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARALLEL_RELATION_NOT_FOUND));
        if (partner.isWithdrawn()) {
            throw new BusinessException(ErrorCode.PARALLEL_RELATION_NOT_FOUND);
        }

        return toDetailResult(relation, userId);
    }

    private ParallelRelationListItemResult toListItemResult(ParallelRelation relation, User partner, Map<Long, Photo> photoByUserId) {
        return new ParallelRelationListItemResult(
                relation.getId(),
                toUserResult(partner, photoByUserId.get(partner.getId())),
                relation.getRelation(),
                parallelRelationResolver.title(relation.getRelation(), relation.getStoryIndex()),
                relation.getSimilarity(),
                relation.getCreatedAt()
        );
    }

    private void checkNotWithdrawn(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void checkPersonaExists(Long userId) {
        if (!personaElementRepository.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.PERSONA_NOT_FOUND);
        }
    }

    private Map<PersonaDimension, List<String>> personaElements(Long userId) {
        return personaElementRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                .collect(Collectors.groupingBy(
                        PersonaElement::getDimension,
                        Collectors.mapping(PersonaElement::getExplanation, Collectors.toList())
                ));
    }

    private ParallelRelationDetailResult toDetailResult(ParallelRelation pair, Long userId) {
        Long partnerId = pair.partnerIdOf(userId);
        Map<Long, User> userById = userRepository.findAllById(List.of(userId, partnerId)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Photo> photoByUserId = photoRepository.findAllByUserIdInAndType(List.of(userId, partnerId), PhotoType.PROFILE).stream()
                .collect(Collectors.toMap(Photo::getUserId, Function.identity()));

        User codeOwner = userById.get(pair.getCodeOwnerId());
        User submitter = userById.get(pair.submitterId());
        ParallelRelationResult rendered = parallelRelationResolver.render(
                pair.getRelation(),
                pair.getStoryIndex(),
                codeOwner.displayGivenName(),
                submitter.displayGivenName()
        );

        return new ParallelRelationDetailResult(
                pair.getId(),
                toUserResult(userById.get(userId), photoByUserId.get(userId)),
                toUserResult(userById.get(partnerId), photoByUserId.get(partnerId)),
                pair.getSimilarity(),
                pair.getRelation(),
                rendered.title(),
                rendered.story(),
                pair.getCreatedAt()
        );
    }

    private ParallelRelationUserResult toUserResult(User user, Photo photo) {
        return new ParallelRelationUserResult(user.getId(), user.displayGivenName(), toProfilePhotoInfo(user, photo));
    }

    private ProfilePhotoInfo toProfilePhotoInfo(User user, Photo photo) {
        if (user.isWithdrawn() || photo == null) {
            return null;
        }

        return new ProfilePhotoInfo(photo.getKey(), cloudFrontService.getSignedUrl(photo.getKey()), photo.position());
    }
}
