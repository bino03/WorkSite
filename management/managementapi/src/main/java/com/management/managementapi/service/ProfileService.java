package com.management.managementapi.service;

import com.management.managementapi.dto.profile.EmailUpdateRequest;
import com.management.managementapi.dto.profile.PasswordUpdateRequest;
import com.management.managementapi.dto.profile.ProfileDTO;
import com.management.managementapi.dto.profile.ProfileUpdateRequest;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.User;
import com.management.managementapi.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.ValidationException;

import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.lang.NonNull;


@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupabaseStorageService storage;
    @PersistenceContext
    private EntityManager em;

    public ProfileService(ProfileRepository profileRepository, UserRepository userRepository,
                          PasswordEncoder passwordEncoder, SupabaseStorageService storage) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
    }

 public Optional<ProfileDTO> getProfileByUserId(UUID authUserId) {
        Optional<Tuple> tupleOpt = profileRepository.findProfileWithEmailByAuthUserId(authUserId);

        if (tupleOpt.isPresent()) {
            Tuple tuple = tupleOpt.get();
            Profile profile = (Profile) tuple.get(0);  // Profile object is at index 0
            String email = (String) tuple.get(1);  // Email is at index 1

            ProfileDTO profileDTO = new ProfileDTO();
            profileDTO.setName(profile.getName());
            profileDTO.setPhotoUrl(resolvePhotoUrl(profile));
            profileDTO.setPhoneNumber(profile.getPhoneNumber());
            profileDTO.setRole(profile.getRole().name());
            profileDTO.setAccountStatus(profile.getAccountStatus().name());
            profileDTO.setCreatedAt(profile.getCreatedAt());
            profileDTO.setUpdatedAt(profile.getUpdatedAt());
            profileDTO.setPhotoBucket(profile.getPhotoBucket());
            profileDTO.setPhotoKey(profile.getPhotoKey());
            profileDTO.setEmail(email);  // Set the email

            return Optional.of(profileDTO);
        }

        return Optional.empty();
    }


    // Atualizar nome e telefone
   @Transactional
    public void updateNamePhone(UUID authUserId, ProfileUpdateRequest req) {
        // validações simples
        String name = req.getName() == null ? null : req.getName().trim();
        String phone = req.getPhoneNumber() == null ? null : req.getPhoneNumber().trim();

        if (name == null || name.isBlank())
            throw new ValidationException("Nome não pode ser vazio.");

        if (phone == null || !phone.matches("\\+?[0-9]+"))
            throw new ValidationException("Número de telefone inválido.");

        // defesa contra locks longos (opcional, mas útil)
        em.createNativeQuery("set local lock_timeout = '3s'").executeUpdate();

        int n = profileRepository.updateNamePhone(authUserId, name, phone);
        if (n != 1) {
            throw new ValidationException("Perfil não encontrado.");
        }
    }

    // Atualizar email
    public void updateEmail(@NonNull UUID authUserId, EmailUpdateRequest request) {
        if (request.getEmail() == null || !request.getEmail().matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            throw new ValidationException("Email inválido.");
        }

        // Verificar se o email já existe na base de dados
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("Este email já está em uso.");
        }

        User user = userRepository.findById(authUserId)
                .orElseThrow(() -> new ValidationException("Usuário não encontrado."));

        user.setEmail(request.getEmail());  // Alterando o email do usuário
        userRepository.save(user);  // Salvando o usuário
    }

    // Atualizar senha
    public void updatePassword(@NonNull UUID authUserId, PasswordUpdateRequest request) {
    // As validações @Valid já cobrem null/size, mas podes manter este guard se quiseres.
    if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
        throw new ValidationException("A nova palavra-passe deve ter pelo menos 8 caracteres.");
    }

    User user = userRepository.findById(authUserId)
            .orElseThrow(() -> new ValidationException("Utilizador não encontrado."));

    // 1) confirmar password atual
    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getEncryptedPassword())) {
        throw new ValidationException("A palavra-passe atual está incorreta.");
    }

    // 2) impedir reutilização
    if (passwordEncoder.matches(request.getNewPassword(), user.getEncryptedPassword())) {
        throw new ValidationException("A nova palavra-passe não pode ser igual à atual.");
    }

    // 3) aplicar alteração
    user.setEncryptedPassword(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);
}

//  /**
//      * Lista todos os profiles exceto o do utilizador autenticado
//      */
//     @Transactional
//     public List<ProfileListResponseDTO> getAllProfilesExceptCurrent(UUID currentAuthUserId) {
//         return profileRepository.findAllProfilesExceptCurrent(currentAuthUserId);
//     }

    private String resolvePhotoUrl(Profile profile) {
        if (profile == null || profile.getPhotoKey() == null) return null;
        try {
            String bucket = profile.getPhotoBucket();
            String key = profile.getPhotoKey().startsWith("/") ? profile.getPhotoKey().substring(1) : profile.getPhotoKey();
            return storage.createSignedUrl(bucket, key, 3600);
        } catch (Exception e) {
            log.warn("Não foi possível gerar signed URL para a foto do perfil: {}", e.getMessage());
            return null;
        }
    }
}