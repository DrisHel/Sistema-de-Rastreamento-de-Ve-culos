package br.com.rastreamento;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try (MongoLocationRepository repository = new MongoLocationRepository();
             RedisLocationCache cache = new RedisLocationCache();
             Scanner scanner = new Scanner(System.in)) {
            runMenu(scanner, new TrackingService(repository, cache));
        } catch (Exception exception) {
            System.err.println("Nao foi possivel iniciar ou manter a conexao com os bancos de dados.");
            System.err.println(exception.getMessage());
        }
    }

    private static void runMenu(Scanner scanner, TrackingService service) {
        while (true) {
            System.out.println("\n=== Rastreamento de Veiculos ===");
            System.out.println("1. Registrar localizacao");
            System.out.println("2. Consultar ultima localizacao");
            System.out.println("3. Limpar historico antigo");
            System.out.println("0. Sair");
            System.out.print("Escolha: ");

            String option = scanner.nextLine().trim();
            try {
                switch (option) {
                    case "1" -> registerLocation(scanner, service);
                    case "2" -> findLocation(scanner, service);
                    case "3" -> cleanHistory(scanner, service);
                    case "0" -> {
                        System.out.println("Encerrando.");
                        return;
                    }
                    default -> System.out.println("Opcao invalida.");
                }
            } catch (RuntimeException exception) {
                System.out.println("Operacao nao concluida: " + exception.getMessage());
            }
        }
    }

    private static void registerLocation(Scanner scanner, TrackingService service) {
        String vehicleId = readVehicleId(scanner);
        double latitude = readCoordinate(scanner, "Latitude", -90, 90);
        double longitude = readCoordinate(scanner, "Longitude", -180, 180);
        service.register(vehicleId, latitude, longitude);
        System.out.println("Localizacao registrada no MongoDB e atualizada no Redis.");
    }

    private static void findLocation(Scanner scanner, TrackingService service) {
        String vehicleId = readVehicleId(scanner);
        VehicleLocation location = service.findLatest(vehicleId);
        if (location == null) {
            System.out.println("Nenhuma localizacao encontrada para esse veiculo.");
            return;
        }

        System.out.printf("Veiculo: %s | Latitude: %.6f | Longitude: %.6f | Data: %s%n",
                location.vehicleId(), location.latitude(), location.longitude(), location.recordedAt());
    }

    private static void cleanHistory(Scanner scanner, TrackingService service) {
        long days = readPositiveLong(scanner, "Remover registros com mais de quantos dias? ");
        long deletedCount = service.cleanHistoryOlderThan(days);
        System.out.printf("Limpeza concluida. Registros removidos: %d.%n", deletedCount);
    }

    private static String readVehicleId(Scanner scanner) {
        while (true) {
            System.out.print("Identificador do veiculo: ");
            String vehicleId = scanner.nextLine().trim();
            if (!vehicleId.isEmpty()) {
                return vehicleId;
            }
            System.out.println("O identificador nao pode ficar vazio.");
        }
    }

    private static double readCoordinate(Scanner scanner, String label, double minimum, double maximum) {
        while (true) {
            System.out.print(label + ": ");
            try {
                double value = Double.parseDouble(scanner.nextLine().trim().replace(',', '.'));
                if (Double.isFinite(value) && value >= minimum && value <= maximum) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.printf("Informe um valor entre %.0f e %.0f.%n", minimum, maximum);
        }
    }

    private static long readPositiveLong(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                long value = Long.parseLong(scanner.nextLine().trim());
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Informe um numero inteiro maior que zero.");
        }
    }
}